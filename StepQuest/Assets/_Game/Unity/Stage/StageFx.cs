using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;

namespace StepQuest.Unity.Stage
{
    /// <summary>
    /// The "HD-2D" post stack: tilt-shift depth of field, bloom on emissive pixels, vignette and per-region grading.
    /// All URP-specific code lives here.
    /// </summary>
    public static class StageFx
    {
        public static void EnablePostProcessing(Camera cam)
        {
            var data = cam.GetUniversalAdditionalCameraData();
            data.renderPostProcessing = true;
            data.antialiasing = AntialiasingMode.None; // keep pixels crisp
            data.renderShadows = true;
        }

        public static Volume CreateVolume(Transform parent)
        {
            var go = new GameObject("PostFX");
            go.transform.SetParent(parent, false);
            var vol = go.AddComponent<Volume>();
            vol.isGlobal = true;
            var profile = ScriptableObject.CreateInstance<VolumeProfile>();
            vol.profile = profile;

            var bloom = profile.Add<Bloom>(true);
            bloom.threshold.Override(0.85f);
            bloom.intensity.Override(0.7f);
            bloom.scatter.Override(0.65f);

            var dof = profile.Add<DepthOfField>(true);
            dof.mode.Override(DepthOfFieldMode.Bokeh);
            dof.focusDistance.Override(11.5f);
            dof.focalLength.Override(60f);
            dof.aperture.Override(4.2f);

            var vignette = profile.Add<Vignette>(true);
            vignette.intensity.Override(0.32f);
            vignette.smoothness.Override(0.5f);

            var color = profile.Add<ColorAdjustments>(true);
            color.postExposure.Override(0.15f);
            color.contrast.Override(8f);
            color.saturation.Override(12f);

            var tone = profile.Add<Tonemapping>(true);
            tone.mode.Override(TonemappingMode.Neutral);

            profile.Add<FilmGrain>(true).intensity.Override(0.12f);
            profile.Add<WhiteBalance>(true);
            return vol;
        }

        public static void Grade(Volume vol, Color tint, float temperature, float focusDistance)
        {
            if (vol == null || vol.profile == null) return;
            if (vol.profile.TryGet<ColorAdjustments>(out var c)) c.colorFilter.Override(tint);
            if (vol.profile.TryGet<WhiteBalance>(out var wb)) wb.temperature.Override(temperature);
            if (vol.profile.TryGet<DepthOfField>(out var dof)) dof.focusDistance.Override(focusDistance);
        }
    }
}
