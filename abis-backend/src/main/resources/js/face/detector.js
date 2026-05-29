var FaceDetectModule = (function () {
  var detector = null;
  var isReady = false;
  var isLoading = false;
  var loadPromise = null;

  var MEDIA_PIPE_ASSET_PATH = '/assets/mediapipe';
  var MODEL_ASSET_PATH = '/assets/mediapipe/face_detector.tflite';

  function setModelPath(path) {
    MODEL_ASSET_PATH = path;
  }

  function initialize() {
    if (isReady) return Promise.resolve(true);
    if (isLoading && loadPromise) return loadPromise;

    isLoading = true;

    var visionModule = null;
    var visionInstance = null;

    loadPromise = import(MEDIA_PIPE_ASSET_PATH + '/vision_bundle.mjs')
      .then(function (vision) {
        visionModule = vision;
        return vision.FilesetResolver.forVisionTasks(MEDIA_PIPE_ASSET_PATH);
      })
      .then(function (instance) {
        visionInstance = instance;
        return visionModule.FaceDetector.createFromOptions(instance, {
          baseOptions: {
            modelAssetPath: MODEL_ASSET_PATH,
            delegate: 'GPU'
          },
          runningMode: 'VIDEO',
          minDetectionConfidence: 0.5,
          minSuppressionThreshold: 0.3
        });
      })
      .catch(function (gpuError) {
        console.warn('GPU delegate fallo, intentando CPU:', String(gpuError));
        if (!visionInstance) {
          throw new Error('Vision no inicializado, no se puede reintentar con CPU');
        }
        return visionModule.FaceDetector.createFromOptions(visionInstance, {
          baseOptions: {
            modelAssetPath: MODEL_ASSET_PATH,
            delegate: 'CPU'
          },
          runningMode: 'VIDEO',
          minDetectionConfidence: 0.5,
          minSuppressionThreshold: 0.3
        });
      })
      .then(function (faceDetector) {
        detector = faceDetector;
        isReady = true;
        isLoading = false;
        loadPromise = null;
        return true;
      })
      .catch(function (err) {
        isLoading = false;
        loadPromise = null;
        visionModule = null;
        visionInstance = null;
        console.error('FaceDetectModule: error de inicializacion:', String(err));
        throw err;
      });

    return loadPromise;
  }

  function detectForVideo(video, timestamp) {
    if (!isReady || !detector) {
      throw new Error('FaceDetectModule: detector no inicializado');
    }
    return detector.detectForVideo(video, timestamp || performance.now());
  }

  function getIsReady() {
    return isReady;
  }

  function getIsLoading() {
    return isLoading;
  }

  return {
    setModelPath: setModelPath,
    initialize: initialize,
    detectForVideo: detectForVideo,
    isReady: getIsReady,
    isLoading: getIsLoading
  };
})();

window.FaceDetectModule = FaceDetectModule;
