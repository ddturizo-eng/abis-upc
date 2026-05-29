var CaptureFlow = {
  CROP_URL: 'http://localhost:8001/face/crop',
  JAVA_UPLOAD_URL: 'http://localhost:7000/api/votantes/foto',
  TIMEOUT_MS: 5000,

  execute: function (originalBlob, identificacion) {
    var cropUrl = CaptureFlow.CROP_URL;
    var uploadUrl = CaptureFlow.JAVA_UPLOAD_URL;
    var timeoutMs = CaptureFlow.TIMEOUT_MS;

    return cropWithPython(originalBlob, cropUrl, timeoutMs)
      .then(function (croppedBlob) {
        return uploadToJava(croppedBlob, identificacion, uploadUrl);
      });
  },

  executeWithFallback: function (originalBlob, identificacion) {
    var cropUrl = CaptureFlow.CROP_URL;
    var uploadUrl = CaptureFlow.JAVA_UPLOAD_URL;
    var timeoutMs = CaptureFlow.TIMEOUT_MS;

    return cropWithPython(originalBlob, cropUrl, timeoutMs)
      .catch(function (err) {
        console.warn('CaptureFlow: crop fallo, usando imagen original:', err.message);
        return originalBlob;
      })
      .then(function (blobToUpload) {
        return uploadToJava(blobToUpload, identificacion, uploadUrl);
      });
  }
};

function cropWithPython(blob, url, timeoutMs) {
  return new Promise(function (resolve, reject) {
    var formData = new FormData();
    formData.append('foto', blob, 'frame.jpg');

    var controller = new AbortController();
    var timeout = setTimeout(function () {
      controller.abort();
      reject(new Error('Timeout al recortar rostro (' + timeoutMs + 'ms)'));
    }, timeoutMs);

    fetch(url, {
      method: 'POST',
      body: formData,
      signal: controller.signal
    })
      .then(function (response) {
        clearTimeout(timeout);
        if (!response.ok) {
          return reject(new Error('Crop HTTP ' + response.status));
        }
        return response.blob();
      })
      .then(resolve)
      .catch(function (err) {
        clearTimeout(timeout);
        reject(err);
      });
  });
}

function uploadToJava(blob, identificacion, url) {
  return new Promise(function (resolve, reject) {
    var formData = new FormData();
    formData.append('foto', blob, 'rostro.jpg');
    formData.append('identificacion', identificacion);

    fetch(url, {
      method: 'POST',
      body: formData
    })
      .then(function (response) {
        if (!response.ok) {
          return reject(new Error('Upload HTTP ' + response.status));
        }
        return response.json();
      })
      .then(resolve)
      .catch(reject);
  });
}

window.CaptureFlow = CaptureFlow;
