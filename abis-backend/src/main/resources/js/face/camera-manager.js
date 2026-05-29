var CameraManager = function () {
  var stream = null;
  var videoEl = null;

  function open(videoElement, constraints) {
    if (stream) {
      stop();
    }

    var defaultConstraints = { width: 640, height: 480, facingMode: 'user' };
    var finalConstraints = constraints || defaultConstraints;

    return navigator.mediaDevices.getUserMedia({ video: finalConstraints })
      .then(function (mediaStream) {
        stream = mediaStream;
        videoEl = videoElement;
        videoEl.srcObject = stream;
        videoEl.setAttribute('autoplay', '');
        videoEl.setAttribute('playsinline', '');

        return new Promise(function (resolve) {
          videoEl.onloadedmetadata = function () {
            videoEl.play().then(resolve).catch(resolve);
          };
          if (videoEl.readyState >= 2) {
            videoEl.play().then(resolve).catch(resolve);
          }
        });
      });
  }

  function stop() {
    if (stream) {
      stream.getTracks().forEach(function (track) { track.stop(); });
      stream = null;
    }
    if (videoEl) {
      videoEl.srcObject = null;
      videoEl = null;
    }
  }

  function isActive() {
    return stream !== null && stream.active === true;
  }

  function getStream() {
    return stream;
  }

  function getVideo() {
    return videoEl;
  }

  return {
    open: open,
    stop: stop,
    isActive: isActive,
    getStream: getStream,
    getVideo: getVideo
  };
};

window.CameraManager = CameraManager;
