using System;
using System.Net;
using System.Net.Http;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
using System.Windows.Forms;
using System.IO;
using Newtonsoft.Json;
using DPFP;
using DPFP.Capture;
using DPFP.Processing;
using DPFP.Verification;

namespace NativeService
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.Run(new HiddenForm());
        }
    }

    public class HiddenForm : Form
    {
        private HttpListener listener;
        public BiometricHandler Handler;

        public HiddenForm()
        {
            this.WindowState = FormWindowState.Minimized;
            this.ShowInTaskbar = false;
            this.Opacity = 0;
            this.Text = "NativeService";
            this.Load += OnFormLoad;
        }

        private async void OnFormLoad(object sender, EventArgs e)
        {
            Log("Formulario cargado, inicializando handler...");
            Handler = new BiometricHandler(this);
            Log("Handler creado, iniciando servidor HTTP...");
            await StartHttpServer();
        }

        private async Task StartHttpServer()
        {
            try
            {
                listener = new HttpListener();
                listener.Prefixes.Add("http://localhost:8765/");
                listener.Start();
                Log("Escuchando en http://localhost:8765/");
                while (true)
                {
                    var context = await listener.GetContextAsync();
                    _ = Task.Run(() => HandleRequest(context));
                }
            }
            catch (Exception ex)
            {
                Log("Error en servidor HTTP: " + ex.Message);
            }
        }

        private async Task HandleRequest(HttpListenerContext context)
        {
            var path = context.Request.Url.AbsolutePath.TrimEnd('/');
            Log("Request: " + context.Request.HttpMethod + " " + path);
            string responseJson;
            context.Response.ContentType = "application/json";
            context.Response.Headers.Add("Access-Control-Allow-Origin", "*");
            try
            {
                if (path == "/status")
                    responseJson = Handler.GetStatus();
                else if (path == "/capture")
                    responseJson = await Handler.Capture();
                else if (path == "/enroll")
                    responseJson = await Handler.Enroll(context);
                else if (path == "/enroll-live")
                    responseJson = await Handler.EnrollLive(context);
                else if (path == "/identify")
                    responseJson = await Handler.Identify(context);
                else
                    responseJson = JsonConvert.SerializeObject(new { error = "endpoint no encontrado" });
            }
            catch (Exception ex)
            {
                Log("Error handling request: " + ex.Message);
                context.Response.StatusCode = 500;
                responseJson = JsonConvert.SerializeObject(new { error = ex.Message });
            }
            var buffer = Encoding.UTF8.GetBytes(responseJson);
            context.Response.ContentLength64 = buffer.Length;
            await context.Response.OutputStream.WriteAsync(buffer, 0, buffer.Length);
            context.Response.Close();
            Log("Response: " + responseJson.Substring(0, Math.Min(100, responseJson.Length)));
        }

        public static void Log(string msg)
        {
            Console.WriteLine("[" + DateTime.Now.ToString("HH:mm:ss") + "] " + msg);
        }
    }

    public class BiometricHandler : DPFP.Capture.EventHandler
        {
            private readonly Form form;
            private Capture capturer;
            private Sample lastSample;
            private Sample pendingSample;
            private DateTime pendingSampleAt = DateTime.MinValue;
            private readonly SemaphoreSlim captureSemaphore = new SemaphoreSlim(0, 1);
            private readonly SemaphoreSlim requestLock = new SemaphoreSlim(1, 1);
            private static readonly HttpClient httpClient = new HttpClient();
            private bool readerReady = false;
            private bool capturing = false;
            private const int CaptureTimeoutMs = 6000;
            private const int PendingSampleTtlMs = 10000;
            private const int LiveEnrollTimeoutMs = 60000;
            private bool liveEnrollmentActive = false;
            private int liveSampleCount = 0;
            private string liveVoterId = "";
            private string liveProgressCallbackUrl = "";
            private DPFP.Processing.Enrollment liveEnrollment;
            private DPFP.Processing.FeatureExtraction liveExtractor;
            private TaskCompletionSource<LiveEnrollResult> liveEnrollCompletion;

            public BiometricHandler(Form form)
            {
                this.form = form;
                capturer = new Capture(Priority.Low);
                capturer.EventHandler = this;
                capturer.StartCapture();
                HiddenForm.Log("Capturer iniciado OK");
            }

            public string GetStatus()
            {
                return JsonConvert.SerializeObject(new
                {
                    status = readerReady ? "ready" : "waiting",
                    reader = "U.are.U 4500",
                    service = "ok",
                    capturing = capturing
                });
            }

            public async Task<string> Capture()
            {
                await requestLock.WaitAsync();
                try
                {
                    capturing = true;
                    lastSample = null;

                    // Drenar semáforo completamente antes de esperar
                    while (captureSemaphore.CurrentCount > 0)
                        await captureSemaphore.WaitAsync();

                    if (pendingSample != null && (DateTime.UtcNow - pendingSampleAt).TotalMilliseconds <= PendingSampleTtlMs)
                    {
                        lastSample = pendingSample;
                        pendingSample = null;
                        var cachedB64 = Convert.ToBase64String(lastSample.Bytes);
                        HiddenForm.Log("Sample OK desde cache - " + lastSample.Bytes.Length + " bytes");
                        return JsonConvert.SerializeObject(new { success = true, sample = cachedB64 });
                    }

                    HiddenForm.Log("Esperando huella (6s)... coloque el dedo ahora");
                    bool captured = await Task.Run(() => captureSemaphore.Wait(CaptureTimeoutMs));

                    if (!captured || lastSample == null)
                    {
                        HiddenForm.Log("Timeout");
                        return JsonConvert.SerializeObject(new { success = false, error = "timeout o no se capturo huella" });
                    }

                    var sampleB64 = Convert.ToBase64String(lastSample.Bytes);
                    HiddenForm.Log("Sample OK - " + lastSample.Bytes.Length + " bytes");
                    return JsonConvert.SerializeObject(new { success = true, sample = sampleB64 });
                }
                finally
                {
                    capturing = false;
                    requestLock.Release();
                }
            }

            public async Task<string> Enroll(HttpListenerContext context)
            {
                string body;
                using (var reader = new System.IO.StreamReader(context.Request.InputStream))
                    body = await reader.ReadToEndAsync();

                var data = JsonConvert.DeserializeObject<EnrollRequest>(body);
                if (data == null || data.Samples == null || data.Samples.Length == 0)
                    return JsonConvert.SerializeObject(new { success = false, error = "samples vacios" });

                HiddenForm.Log("Enrolando con " + data.Samples.Length + " samples...");
                var enrollment = new DPFP.Processing.Enrollment();
                var extractor = new DPFP.Processing.FeatureExtraction();

                foreach (var sampleB64 in data.Samples)
                {
                    var sampleBytes = Convert.FromBase64String(sampleB64);
                    var sample = new DPFP.Sample(new System.IO.MemoryStream(sampleBytes));
                    var feedback = DPFP.Capture.CaptureFeedback.None;
                    var features = new DPFP.FeatureSet();
                    extractor.CreateFeatureSet(sample, DPFP.Processing.DataPurpose.Enrollment, ref feedback, ref features);

                    HiddenForm.Log("Feature feedback: " + feedback);
                    if (feedback != DPFP.Capture.CaptureFeedback.Good)
                        return JsonConvert.SerializeObject(new { success = false, error = "calidad insuficiente: " + feedback });

                    enrollment.AddFeatures(features);
                    HiddenForm.Log("FeaturesNeeded: " + enrollment.FeaturesNeeded);
                }

                if (enrollment.TemplateStatus != DPFP.Processing.Enrollment.Status.Ready)
                    return JsonConvert.SerializeObject(new
                    {
                        success = false,
                        error = "template no listo",
                        features_needed = enrollment.FeaturesNeeded
                    });

                var templateB64 = Convert.ToBase64String(enrollment.Template.Bytes);
                HiddenForm.Log("Template OK - " + enrollment.Template.Bytes.Length + " bytes");
                return JsonConvert.SerializeObject(new { success = true, template = templateB64 });
            }

            public async Task<string> EnrollLive(HttpListenerContext context)
            {
                await requestLock.WaitAsync();
                try
                {
                    string body;
                    using (var reader = new System.IO.StreamReader(context.Request.InputStream))
                        body = await reader.ReadToEndAsync();

                    var data = JsonConvert.DeserializeObject<LiveEnrollRequest>(body);
                    if (data == null || string.IsNullOrWhiteSpace(data.Identificacion))
                        return JsonConvert.SerializeObject(new { success = false, error = "identificacion requerida" });

                    liveVoterId = data.Identificacion;
                    liveProgressCallbackUrl = data.ProgressCallbackUrl;
                    liveSampleCount = 0;
                    liveEnrollment = new DPFP.Processing.Enrollment();
                    liveExtractor = new DPFP.Processing.FeatureExtraction();
                    liveEnrollCompletion = new TaskCompletionSource<LiveEnrollResult>();
                    liveEnrollmentActive = true;

                    NotifyProgress("ESPERANDO_DEDO", 0, 0, "Coloque el dedo sobre el lector.");
                    HiddenForm.Log("Enrolamiento live iniciado para " + liveVoterId);

                    var completed = await Task.WhenAny(
                        liveEnrollCompletion.Task,
                        Task.Delay(LiveEnrollTimeoutMs)
                    );

                    if (completed != liveEnrollCompletion.Task)
                    {
                        liveEnrollmentActive = false;
                        NotifyProgress("ERROR", liveSampleCount, liveSampleCount * 25, "Tiempo agotado capturando huella.");
                        return JsonConvert.SerializeObject(new { success = false, error = "timeout capturando huella" });
                    }

                    var result = await liveEnrollCompletion.Task;
                    return JsonConvert.SerializeObject(result);
                }
                finally
                {
                    liveEnrollmentActive = false;
                    liveEnrollment = null;
                    liveExtractor = null;
                    liveEnrollCompletion = null;
                    requestLock.Release();
                }
            }

            public async Task<string> Identify(HttpListenerContext context)
            {
                string body;
                using (var reader = new System.IO.StreamReader(context.Request.InputStream))
                    body = await reader.ReadToEndAsync();

                var data = JsonConvert.DeserializeObject<IdentifyRequest>(body);
                if (data == null || data.Sample == null || data.Templates == null || data.Templates.Length == 0)
                    return JsonConvert.SerializeObject(new { matched = false, error = "datos incompletos" });

                HiddenForm.Log("Identificando contra " + data.Templates.Length + " templates...");

                var sampleBytes = Convert.FromBase64String(data.Sample);
                var probeSample = new DPFP.Sample(new System.IO.MemoryStream(sampleBytes));
                var extractor = new DPFP.Processing.FeatureExtraction();
                var feedback = DPFP.Capture.CaptureFeedback.None;
                var probeFeatures = new DPFP.FeatureSet();
                extractor.CreateFeatureSet(probeSample, DPFP.Processing.DataPurpose.Verification, ref feedback, ref probeFeatures);

                HiddenForm.Log("Probe feedback: " + feedback);
                if (feedback != DPFP.Capture.CaptureFeedback.Good)
                    return JsonConvert.SerializeObject(new { matched = false, error = "calidad insuficiente: " + feedback });

                var verifier = new DPFP.Verification.Verification();
                for (int i = 0; i < data.Templates.Length; i++)
                {
                    var templateBytes = Convert.FromBase64String(data.Templates[i]);
                    var template = new DPFP.Template(new System.IO.MemoryStream(templateBytes));
                    var result = new DPFP.Verification.Verification.Result();
                    verifier.Verify(probeFeatures, template, ref result);
                    HiddenForm.Log("User " + data.UserIds[i] + ": verified=" + result.Verified);
                    if (result.Verified)
                        return JsonConvert.SerializeObject(new { matched = true, user_id = data.UserIds[i] });
                }

                HiddenForm.Log("No match");
                return JsonConvert.SerializeObject(new { matched = false });
            }

            public void OnComplete(object capture, string readerSerialNumber, Sample sample)
            {
                HiddenForm.Log("HUELLA CAPTURADA - " + readerSerialNumber + " - " + sample.Bytes.Length + " bytes");
                if (liveEnrollmentActive)
                {
                    ProcessLiveEnrollmentSample(sample);
                    return;
                }
                if (capturing && captureSemaphore.CurrentCount == 0)
                {
                    lastSample = sample;
                    pendingSample = null;
                    captureSemaphore.Release();
                }
                else
                {
                    pendingSample = sample;
                    pendingSampleAt = DateTime.UtcNow;
                    HiddenForm.Log("Huella guardada temporalmente para la siguiente captura");
                }
            }

            public void OnFingerGone(object capture, string readerSerialNumber)
                => HiddenForm.Log("Dedo retirado");

            public void OnFingerTouch(object capture, string readerSerialNumber)
                => HiddenForm.Log("Dedo detectado");

            public void OnReaderConnect(object capture, string readerSerialNumber)
            {
                readerReady = true;
                HiddenForm.Log("Lector conectado: " + readerSerialNumber);
            }

            public void OnReaderDisconnect(object capture, string readerSerialNumber)
            {
                readerReady = false;
                HiddenForm.Log("Lector desconectado");
            }

            public void OnSampleQuality(object capture, string readerSerialNumber, CaptureFeedback feedback)
                => HiddenForm.Log("Calidad: " + feedback);

            private void ProcessLiveEnrollmentSample(Sample sample)
            {
                try
                {
                    var feedback = DPFP.Capture.CaptureFeedback.None;
                    var features = new DPFP.FeatureSet();
                    liveExtractor.CreateFeatureSet(sample, DPFP.Processing.DataPurpose.Enrollment, ref feedback, ref features);
                    HiddenForm.Log("Live feature feedback: " + feedback);

                    if (feedback != DPFP.Capture.CaptureFeedback.Good)
                    {
                        NotifyProgress(
                            "PROCESANDO_CAPTURA",
                            liveSampleCount,
                            liveSampleCount * 25,
                            "Calidad insuficiente. Retire el dedo e intente nuevamente."
                        );
                        return;
                    }

                    liveEnrollment.AddFeatures(features);
                    liveSampleCount++;
                    int progress = liveSampleCount * 25;

                    if (liveSampleCount < 4)
                    {
                        NotifyProgress(
                            "PROCESANDO_CAPTURA",
                            liveSampleCount,
                            progress,
                            "Muestra capturada. Levante y coloque el dedo nuevamente."
                        );
                        return;
                    }

                    NotifyProgress("PROCESANDO_MINUCIAS", 4, 100, "Validando y registrando en censo...");

                    if (liveEnrollment.TemplateStatus == DPFP.Processing.Enrollment.Status.Ready)
                    {
                        string templateB64 = Convert.ToBase64String(liveEnrollment.Template.Bytes);
                        liveEnrollmentActive = false;
                        liveEnrollCompletion.TrySetResult(new LiveEnrollResult
                        {
                            Success = true,
                            Template = templateB64,
                            Samples = liveSampleCount
                        });
                    }
                    else
                    {
                        liveEnrollmentActive = false;
                        NotifyProgress("ERROR", liveSampleCount, progress, "Calidad de huella insuficiente. Intente de nuevo.");
                        liveEnrollCompletion.TrySetResult(new LiveEnrollResult
                        {
                            Success = false,
                            Error = "template no listo",
                            Samples = liveSampleCount,
                            FeaturesNeeded = (int)liveEnrollment.FeaturesNeeded
                        });
                    }
                }
                catch (Exception ex)
                {
                    liveEnrollmentActive = false;
                    NotifyProgress("ERROR", liveSampleCount, liveSampleCount * 25, ex.Message);
                    liveEnrollCompletion.TrySetResult(new LiveEnrollResult
                    {
                        Success = false,
                        Error = ex.Message,
                        Samples = liveSampleCount
                    });
                }
            }

            private void NotifyProgress(string estado, int samples, int progreso, string mensaje)
            {
                if (string.IsNullOrWhiteSpace(liveProgressCallbackUrl))
                    return;

                var payload = JsonConvert.SerializeObject(new
                {
                    identificacion = liveVoterId,
                    estado = estado,
                    samples = samples,
                    progreso = progreso,
                    mensaje = mensaje
                });
                _ = Task.Run(async () =>
                {
                    try
                    {
                        var content = new StringContent(payload, Encoding.UTF8, "application/json");
                        await httpClient.PostAsync(liveProgressCallbackUrl, content);
                    }
                    catch (Exception ex)
                    {
                        HiddenForm.Log("No se pudo notificar progreso a Java: " + ex.Message);
                    }
                });
            }
        }

        public class EnrollRequest
    {
        public string[] Samples { get; set; }
    }

    public class LiveEnrollRequest
    {
        [JsonProperty("identificacion")]
        public string Identificacion { get; set; }

        [JsonProperty("progressCallbackUrl")]
        public string ProgressCallbackUrl { get; set; }
    }

    public class LiveEnrollResult
    {
        [JsonProperty("success")]
        public bool Success { get; set; }

        [JsonProperty("template")]
        public string Template { get; set; }

        [JsonProperty("error")]
        public string Error { get; set; }

        [JsonProperty("samples")]
        public int Samples { get; set; }

        [JsonProperty("features_needed")]
        public int FeaturesNeeded { get; set; }
    }

    public class IdentifyRequest
    {
        public string Sample { get; set; }
        public string[] Templates { get; set; }
        public string[] UserIds { get; set; }
    }
}
