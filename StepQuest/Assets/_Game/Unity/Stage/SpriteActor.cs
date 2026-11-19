using System.Collections;
using UnityEngine;

namespace StepQuest.Unity.Stage
{
    /// <summary>
    /// A pixel sprite standing in the 3D diorama: an alpha-clipped lit quad that faces the camera,
    /// casts a real shadow, and can lunge, flash, shake and fade.
    /// </summary>
    public sealed class SpriteActor : MonoBehaviour
    {
        public const float PixelsPerUnit = 16f;

        public int UnitId = -1;
        public bool Bob = true;
        public float BobAmount = 0.04f;

        MeshRenderer _renderer;
        Material _mat;
        Transform _visual;
        Vector3 _home;
        float _phase;
        float _baseH;
        Camera _cam;
        static readonly int BaseMap = Shader.PropertyToID("_BaseMap");
        static readonly int BaseColor = Shader.PropertyToID("_BaseColor");
        static readonly int EmissionColor = Shader.PropertyToID("_EmissionColor");

        public static SpriteActor Create(string name, Texture2D tex, Transform parent, Vector3 pos, Material template, bool castShadow = true)
        {
            var go = new GameObject(name);
            go.transform.SetParent(parent, false);
            go.transform.localPosition = pos;
            var actor = go.AddComponent<SpriteActor>();

            var visual = GameObject.CreatePrimitive(PrimitiveType.Quad);
            visual.name = "Visual";
            visual.transform.SetParent(go.transform, false);
            float w = tex.width / PixelsPerUnit, h = tex.height / PixelsPerUnit;
            visual.transform.localScale = new Vector3(w, h, 1);
            visual.transform.localPosition = new Vector3(0, h / 2f, 0);
            var col = visual.GetComponent<MeshCollider>();
            if (col != null) Destroy(col);
            var box = visual.AddComponent<BoxCollider>();
            box.size = new Vector3(1, 1, 0.2f);

            actor._renderer = visual.GetComponent<MeshRenderer>();
            actor._mat = new Material(template);
            actor._mat.SetTexture(BaseMap, tex);
            actor._mat.mainTexture = tex;
            actor._renderer.sharedMaterial = actor._mat;
            actor._renderer.shadowCastingMode = castShadow ? UnityEngine.Rendering.ShadowCastingMode.On : UnityEngine.Rendering.ShadowCastingMode.Off;
            actor._renderer.receiveShadows = false;
            actor._visual = visual.transform;
            actor._baseH = h;
            actor._home = pos;
            actor._phase = Random.value * 10f;
            return actor;
        }

        public float Height => _baseH;
        public Vector3 Home { get => _home; set { _home = value; transform.localPosition = value; } }
        public Vector3 TopWorld => transform.position + Vector3.up * (Height + 0.2f);
        public Vector3 CenterWorld => transform.position + Vector3.up * (Height * 0.5f);

        void LateUpdate()
        {
            if (_cam == null) _cam = Camera.main;
            if (_cam != null)
            {
                // Billboard around the Y axis only, so sprites stay upright like in HD-2D dioramas.
                var fwd = _cam.transform.forward; fwd.y = 0;
                if (fwd.sqrMagnitude > 0.001f) transform.rotation = Quaternion.LookRotation(fwd);
            }
            if (Bob && _visual != null)
            {
                // Gentle "breathing": squash and stretch from the feet.
                float h = _baseH * (1f + Mathf.Sin(Time.time * 2.2f + _phase) * BobAmount);
                _visual.localScale = new Vector3(_visual.localScale.x, h, 1);
                _visual.localPosition = new Vector3(0, h / 2f, 0);
            }
        }

        public void SetTint(Color c) { if (_mat != null) _mat.SetColor(BaseColor, c); }

        public IEnumerator Lunge(Vector3 towardWorld, float distance = 0.9f, float time = 0.32f)
        {
            var start = transform.position;
            var dir = towardWorld - start; dir.y = 0; dir.Normalize();
            var peak = start + dir * distance;
            for (float t = 0; t < 1; t += Time.deltaTime / (time * 0.45f)) { transform.position = Vector3.Lerp(start, peak, Ease(t)); yield return null; }
            for (float t = 0; t < 1; t += Time.deltaTime / (time * 0.55f)) { transform.position = Vector3.Lerp(peak, start, Ease(t)); yield return null; }
            transform.position = start;
        }

        public IEnumerator Hop(float height = 0.35f, float time = 0.3f)
        {
            var start = transform.localPosition;
            for (float t = 0; t < 1; t += Time.deltaTime / time) { transform.localPosition = start + Vector3.up * Mathf.Sin(t * Mathf.PI) * height; yield return null; }
            transform.localPosition = start;
        }

        public IEnumerator Flash(Color color, float time = 0.18f)
        {
            if (_mat == null) yield break;
            _mat.EnableKeyword("_EMISSION");
            for (float t = 0; t < 1; t += Time.deltaTime / time) { _mat.SetColor(EmissionColor, color * (1 - t) * 2.2f); yield return null; }
            _mat.SetColor(EmissionColor, Color.black);
        }

        public IEnumerator Shake(float amount = 0.12f, float time = 0.3f)
        {
            var start = transform.localPosition;
            for (float t = 0; t < 1; t += Time.deltaTime / time)
            {
                transform.localPosition = start + new Vector3(Mathf.Sin(t * 60f) * amount * (1 - t), 0, 0);
                yield return null;
            }
            transform.localPosition = start;
        }

        public IEnumerator Defeat(float time = 0.6f)
        {
            Bob = false;
            for (float t = 0; t < 1; t += Time.deltaTime / time)
            {
                SetTint(new Color(1, 1 - t * 0.6f, 1 - t * 0.6f, 1));
                _visual.localScale = new Vector3(_visual.localScale.x, Mathf.Lerp(_baseH, 0.05f, t * t), 1);
                _visual.localPosition = new Vector3(0, _visual.localScale.y / 2f, 0);
                yield return null;
            }
            gameObject.SetActive(false);
        }

        public void SetFallen(bool fallen)
        {
            Bob = !fallen;
            if (fallen) { _visual.localScale = new Vector3(_visual.localScale.x, _baseH, 1); _visual.localPosition = new Vector3(0, _visual.localScale.x * 0.3f, 0); }
            _visual.localRotation = fallen ? Quaternion.Euler(0, 0, 80) : Quaternion.identity;
            SetTint(fallen ? new Color(0.6f, 0.55f, 0.65f) : Color.white);
        }

        static float Ease(float t) { t = Mathf.Clamp01(t); return t * t * (3 - 2 * t); }

        void OnDestroy() { if (_mat != null) Destroy(_mat); }
    }
}
