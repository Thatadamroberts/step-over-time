using System;
using System.Collections;
using System.Collections.Generic;
using System.Linq;
using StepQuest.Core;
using StepQuest.Core.Combat;
using StepQuest.Core.Data;
using StepQuest.Core.Party;
using StepQuest.Core.World;
using StepQuest.Unity.Art;
using UnityEngine;
using UnityEngine.Rendering;

namespace StepQuest.Unity.Stage
{
    /// <summary>
    /// The HD-2D style diorama: a tilted perspective camera over a shallow 3D set, with pixel sprites
    /// as upright lit billboards that cast real shadows, fog for depth, a parallax backdrop, and a
    /// depth-of-field/bloom post stack. Party stands on the right, enemies on the left.
    /// </summary>
    public sealed class BattleStage : MonoBehaviour
    {
        public Camera Cam { get; private set; }
        Transform _set, _units;
        Light _key, _torch;
        Volume _volume;
        ParticleSystem _ambient, _burst;
        Material _litTemplate, _unlitTemplate, _particleMat;
        readonly Dictionary<int, SpriteActor> _actors = new Dictionary<int, SpriteActor>();
        readonly List<SpriteActor> _partyIdle = new List<SpriteActor>();
        string _builtRegion;
        float _lastAspect;

        /// <summary>How far up the screen the action sits (NDC shift), so the UI can use the lower half.</summary>
        public float ViewShift = -0.42f;
        public float HorizontalFov = 62f;

        public static BattleStage Create()
        {
            var go = new GameObject("BattleStage");
            var stage = go.AddComponent<BattleStage>();
            stage.Init();
            return stage;
        }

        void Init()
        {
            _litTemplate = LoadMaterial("Materials/SpriteLit", "Universal Render Pipeline/Simple Lit", true);
            _unlitTemplate = LoadMaterial("Materials/Unlit", "Universal Render Pipeline/Unlit", false);
            _particleMat = LoadMaterial("Materials/Particle", "Universal Render Pipeline/Particles/Unlit", false);

            var camGo = new GameObject("Main Camera") { tag = "MainCamera" };
            camGo.transform.SetParent(transform, false);
            Cam = camGo.AddComponent<Camera>();
            Cam.clearFlags = CameraClearFlags.SolidColor;
            Cam.backgroundColor = Color.black;
            Cam.nearClipPlane = 0.3f;
            Cam.farClipPlane = 80f;
            camGo.transform.position = new Vector3(0, 3.8f, -11f);
            camGo.transform.LookAt(new Vector3(0, 1.1f, 0.6f));
            StageFx.EnablePostProcessing(Cam);
            _volume = StageFx.CreateVolume(transform);

            var keyGo = new GameObject("Key Light");
            keyGo.transform.SetParent(transform, false);
            keyGo.transform.rotation = Quaternion.Euler(48, -35, 0);
            _key = keyGo.AddComponent<Light>();
            _key.type = LightType.Directional;
            _key.shadows = LightShadows.Soft;
            _key.shadowStrength = 0.65f;

            var torchGo = new GameObject("Torch");
            torchGo.transform.SetParent(transform, false);
            torchGo.transform.position = new Vector3(3.2f, 2.2f, -1.2f);
            _torch = torchGo.AddComponent<Light>();
            _torch.type = LightType.Point;
            _torch.range = 7f;
            _torch.intensity = 1.6f;
            _torch.color = new Color(1f, 0.72f, 0.42f);

            _set = new GameObject("Set").transform; _set.SetParent(transform, false);
            _units = new GameObject("Units").transform; _units.SetParent(transform, false);
            _burst = MakeParticles("Burst", 0, 0.5f, 2.5f, 0.12f, false);
            UpdateProjection();
        }

        Material LoadMaterial(string resource, string shaderName, bool alphaClip)
        {
            var m = Resources.Load<Material>(resource);
            if (m != null) return m;
            var shader = Shader.Find(shaderName) ?? Shader.Find("Sprites/Default");
            m = new Material(shader);
            if (alphaClip)
            {
                m.SetFloat("_AlphaClip", 1f);
                m.SetFloat("_Cutoff", 0.5f);
                m.EnableKeyword("_ALPHATEST_ON");
                m.SetFloat("_Smoothness", 0f);
                m.SetFloat("_SpecularHighlights", 0f);
            }
            return m;
        }

        void Update()
        {
            if (!Mathf.Approximately(_lastAspect, Cam.aspect)) UpdateProjection();
            if (_torch != null) _torch.intensity = 1.5f + Mathf.PerlinNoise(Time.time * 3f, 0) * 0.5f; // flicker
        }

        /// <summary>Wide enough for both sides in portrait, with the scene shifted into the upper part of the screen.</summary>
        void UpdateProjection()
        {
            _lastAspect = Cam.aspect;
            Cam.ResetProjectionMatrix();
            Cam.fieldOfView = Camera.HorizontalToVerticalFieldOfView(HorizontalFov, Mathf.Max(0.3f, Cam.aspect));
            if (Cam.aspect < 1f)
            {
                var m = Cam.projectionMatrix;
                m[1, 2] = ViewShift;
                Cam.projectionMatrix = m;
            }
        }

        // ───────────────────────────── Set dressing ─────────────────────────────

        public void BuildRegion(RegionDef region)
        {
            if (_builtRegion == region.Id) return;
            _builtRegion = region.Id;
            foreach (Transform c in _set) Destroy(c.gameObject);
            var pal = RegionPalette.Of(region.Palette);

            _key.color = pal.KeyLight;
            _key.intensity = region.Palette == "ruins" ? 0.8f : 1.15f;
            RenderSettings.ambientMode = AmbientMode.Flat;
            RenderSettings.ambientLight = pal.AmbientLight;
            RenderSettings.fog = true;
            RenderSettings.fogMode = FogMode.Linear;
            RenderSettings.fogColor = pal.Fog;
            RenderSettings.fogStartDistance = 16f;
            RenderSettings.fogEndDistance = 48f;
            Cam.backgroundColor = pal.SkyTop;
            StageFx.Grade(_volume, Color.Lerp(Color.white, pal.KeyLight, 0.25f),
                region.Palette == "desert" ? 18f : region.Palette == "peaks" ? -14f : region.Palette == "ruins" ? -8f : 0f,
                Vector3.Distance(Cam.transform.position, new Vector3(0, 1, 0.5f)));

            // Ground
            var ground = GameObject.CreatePrimitive(PrimitiveType.Quad);
            ground.name = "Ground";
            ground.transform.SetParent(_set, false);
            ground.transform.localRotation = Quaternion.Euler(90, 0, 0);
            ground.transform.localPosition = new Vector3(0, 0, 8);
            ground.transform.localScale = new Vector3(60, 40, 1);
            Destroy(ground.GetComponent<Collider>());
            var gm = new Material(_litTemplate);
            gm.SetFloat("_AlphaClip", 0f); gm.DisableKeyword("_ALPHATEST_ON");
            var gtex = GroundTexture(pal);
            gm.SetTexture("_BaseMap", gtex); gm.mainTexture = gtex;
            gm.SetTextureScale("_BaseMap", new Vector2(15, 10)); gm.mainTextureScale = new Vector2(15, 10);
            var gr = ground.GetComponent<MeshRenderer>();
            gr.sharedMaterial = gm;
            gr.receiveShadows = true;
            gr.shadowCastingMode = ShadowCastingMode.Off;

            // Backdrop: sky gradient and two silhouette layers for parallax depth.
            Backdrop("Sky", SkyTexture(pal), new Vector3(0, 9, 34), new Vector3(110, 34, 1), _unlitTemplate);
            Backdrop("Far", Silhouette(pal.Far, 7, 0.55f, region.Index * 31 + 1), new Vector3(0, 4.5f, 26), new Vector3(90, 14, 1), _unlitTemplate);
            Backdrop("Mid", Silhouette(pal.Mid, 13, 0.4f, region.Index * 31 + 2), new Vector3(0, 2.4f, 17), new Vector3(60, 7, 1), _litTemplate);

            // Props scattered in depth: far ones sit in fog, near ones fall into the depth-of-field blur.
            var rng = new System.Random(region.Index * 977 + 13);
            for (int i = 0; i < 26; i++)
            {
                var kind = pal.Props[rng.Next(pal.Props.Length)];
                float z = (float)(rng.NextDouble() * 12 + 3.5);
                float x = (float)(rng.NextDouble() * 26 - 13);
                if (Mathf.Abs(x) < 6.5f && z < 6f) z += 5f; // keep the arena clear
                var a = SpriteActor.Create("Prop_" + kind, PixelArt.Prop(kind, region.Palette), _set, new Vector3(x, 0, z), _litTemplate);
                a.Bob = false;
            }
            for (int i = 0; i < 5; i++) // foreground framing (blurred by DoF)
            {
                float x = (i % 2 == 0 ? -1 : 1) * (float)(5.5 + rng.NextDouble() * 3);
                var a = SpriteActor.Create("Fore", PixelArt.Prop(i % 2 == 0 ? pal.Props[0] : "grass", region.Palette), _set, new Vector3(x, 0, -5.2f - (float)rng.NextDouble()), _litTemplate, false);
                a.Bob = false;
            }

            if (_ambient != null) Destroy(_ambient.gameObject);
            _ambient = MakeParticles("Ambient", 18, 6f, 0.25f, pal.Fireflies ? 0.09f : 0.05f, true);
            var main = _ambient.main;
            main.startColor = pal.Fireflies ? new Color(1f, 0.95f, 0.5f, 0.9f) : new Color(1f, 1f, 1f, 0.35f);
        }

        void Backdrop(string name, Texture2D tex, Vector3 pos, Vector3 scale, Material template)
        {
            var q = GameObject.CreatePrimitive(PrimitiveType.Quad);
            q.name = name;
            q.transform.SetParent(_set, false);
            q.transform.localPosition = pos;
            q.transform.localScale = scale;
            Destroy(q.GetComponent<Collider>());
            var m = new Material(template);
            m.SetTexture("_BaseMap", tex); m.mainTexture = tex;
            if (template == _unlitTemplate) { m.SetFloat("_AlphaClip", 1f); m.SetFloat("_Cutoff", 0.5f); m.EnableKeyword("_ALPHATEST_ON"); }
            var r = q.GetComponent<MeshRenderer>();
            r.sharedMaterial = m;
            r.shadowCastingMode = ShadowCastingMode.Off;
            r.receiveShadows = false;
        }

        static Texture2D GroundTexture(RegionPalette pal)
        {
            var p = new PixelCanvas(32, 32);
            var rng = new System.Random(7);
            for (int y = 0; y < 32; y++)
                for (int x = 0; x < 32; x++)
                {
                    double n = rng.NextDouble();
                    var c = n < 0.18 ? pal.GroundAlt : pal.Ground;
                    if (n > 0.97) c = Color32.Lerp(pal.Ground, Color.black, 0.15f);
                    p.Set(x, y, c);
                }
            var t = p.ToTexture("ground");
            t.wrapMode = TextureWrapMode.Repeat;
            return t;
        }

        static Texture2D SkyTexture(RegionPalette pal)
        {
            var p = new PixelCanvas(4, 64);
            for (int y = 0; y < 64; y++)
            {
                // Banded gradient: a pixel-art take on the sky.
                float t = Mathf.Floor(y / 64f * 10f) / 10f;
                var c = Color32.Lerp(pal.SkyTop, pal.SkyBottom, t);
                for (int x = 0; x < 4; x++) p.Set(x, y, c);
            }
            var tex = p.ToTexture("sky");
            tex.filterMode = FilterMode.Point;
            return tex;
        }

        static Texture2D Silhouette(Color32 color, int bumps, float roughness, int seed)
        {
            int w = 256, h = 40;
            var p = new PixelCanvas(w, h);
            var rng = new System.Random(seed);
            var heights = new float[bumps + 2];
            for (int i = 0; i < heights.Length; i++) heights[i] = (float)(0.35 + rng.NextDouble() * 0.6);
            for (int x = 0; x < w; x++)
            {
                float fx = x / (float)w * (bumps + 1);
                int i0 = (int)fx; float f = fx - i0;
                float hgt = Mathf.Lerp(heights[i0], heights[i0 + 1], f * f * (3 - 2 * f));
                hgt += (float)(rng.NextDouble() - 0.5) * 0.04f * roughness;
                int top = (int)((1 - hgt) * h);
                for (int y = top; y < h; y++) p.Set(x, y, y < top + 2 ? Color32.Lerp(color, Color.white, 0.12f) : color);
            }
            var t = p.ToTexture("silhouette");
            t.wrapMode = TextureWrapMode.Clamp;
            return t;
        }

        ParticleSystem MakeParticles(string name, float rate, float lifetime, float speed, float size, bool ambient)
        {
            var go = new GameObject(name);
            go.transform.SetParent(transform, false);
            var ps = go.AddComponent<ParticleSystem>();
            ps.Stop(true, ParticleSystemStopBehavior.StopEmittingAndClear);
            var main = ps.main;
            main.startLifetime = lifetime;
            main.startSpeed = speed;
            main.startSize = size;
            main.maxParticles = 300;
            main.simulationSpace = ParticleSystemSimulationSpace.World;
            main.playOnAwake = ambient;
            var em = ps.emission; em.rateOverTime = rate;
            var shape = ps.shape;
            if (ambient)
            {
                shape.shapeType = ParticleSystemShapeType.Box;
                shape.scale = new Vector3(18, 4, 10);
                go.transform.position = new Vector3(0, 2f, 3f);
                var noise = ps.noise; noise.enabled = true; noise.strength = 0.4f; noise.frequency = 0.3f;
            }
            else
            {
                shape.shapeType = ParticleSystemShapeType.Sphere;
                shape.radius = 0.3f;
                main.gravityModifier = 0.4f;
            }
            var col = ps.colorOverLifetime; col.enabled = true;
            var grad = new Gradient();
            grad.SetKeys(new[] { new GradientColorKey(Color.white, 0), new GradientColorKey(Color.white, 1) },
                         new[] { new GradientAlphaKey(0, 0), new GradientAlphaKey(1, 0.2f), new GradientAlphaKey(0, 1) });
            col.color = grad;
            var r = go.GetComponent<ParticleSystemRenderer>();
            var m = new Material(_particleMat);
            var dot = PixelArt.Dot();
            m.SetTexture("_BaseMap", dot); m.mainTexture = dot;
            r.sharedMaterial = m;
            if (ambient) ps.Play();
            return ps;
        }

        // ───────────────────────────── Units ─────────────────────────────

        static Vector3 HeroSlot(int i) => new Vector3(2.3f + 0.5f * i, 0, 2.0f - 0.95f * i);

        static Vector3 EnemySlot(int i, int count, bool boss)
        {
            if (boss) return new Vector3(-3.0f, 0, 0.9f);
            if (count == 1) return new Vector3(-2.8f, 0, 0.4f);
            return new Vector3(-2.4f - 0.9f * (i % 2), 0, 1.6f - 1.5f * i);
        }

        /// <summary>Party idling in the region (home screen).</summary>
        public void ShowParty(IList<Hero> party)
        {
            ClearUnits();
            for (int i = 0; i < party.Count; i++)
            {
                var a = SpriteActor.Create(party[i].Name, PixelArt.Hero(party[i].Class), _units, HeroSlot(i) + new Vector3(-1.6f, 0, 0), _litTemplate);
                a.SetFallen(party[i].Hp <= 0);
                _partyIdle.Add(a);
            }
        }

        public void ShowBattle(Battle b)
        {
            ClearUnits();
            BuildRegion(GameData.Region(b.RegionId) ?? GameData.Regions[0]);
            int hi = 0;
            foreach (var u in b.Heroes)
            {
                var cls = b.SessionHeroClasses[u.HeroIndex];
                var a = SpriteActor.Create(u.Name, PixelArt.Hero(cls), _units, HeroSlot(hi++), _litTemplate);
                a.UnitId = u.Id;
                a.SetFallen(!u.IsAlive);
                _actors[u.Id] = a;
            }
            var enemies = b.Enemies.ToList();
            for (int i = 0; i < enemies.Count; i++)
            {
                var u = enemies[i];
                var a = SpriteActor.Create(u.Name, PixelArt.Enemy(u.EnemyId), _units, EnemySlot(i, enemies.Count, b.IsBoss), _litTemplate);
                a.UnitId = u.Id;
                a.BobAmount = 0.06f;
                if (!u.IsAlive) a.gameObject.SetActive(false);
                _actors[u.Id] = a;
            }
        }

        public void ClearUnits()
        {
            foreach (Transform c in _units) Destroy(c.gameObject);
            _actors.Clear();
            _partyIdle.Clear();
        }

        public SpriteActor Actor(int id) => _actors.TryGetValue(id, out var a) ? a : null;

        /// <summary>Unit under a screen point (for tap-to-target), or -1.</summary>
        public int UnitAtScreen(Vector2 screen)
        {
            var ray = Cam.ScreenPointToRay(screen);
            if (Physics.Raycast(ray, out var hit, 100f))
            {
                var a = hit.collider.GetComponentInParent<SpriteActor>();
                if (a != null) return a.UnitId;
            }
            return -1;
        }

        public Vector3 ScreenPointOf(int unitId, bool top = true)
        {
            var a = Actor(unitId);
            if (a == null) return new Vector3(-1000, -1000, 0);
            return Cam.WorldToScreenPoint(top ? a.TopWorld : a.CenterWorld);
        }

        public void Highlight(int unitId)
        {
            foreach (var kv in _actors)
                if (kv.Value != null && kv.Value.gameObject.activeSelf && kv.Value.Bob)
                    kv.Value.SetTint(kv.Key == unitId ? new Color(1.15f, 1.1f, 0.9f) : Color.white);
        }

        // ───────────────────────────── Playback ─────────────────────────────

        public static Color ElementColor(Element e)
        {
            switch (e)
            {
                case Element.Fire: return new Color(1f, 0.45f, 0.15f);
                case Element.Ice: return new Color(0.55f, 0.9f, 1f);
                case Element.Lightning: return new Color(1f, 0.95f, 0.35f);
                case Element.Nature: return new Color(0.5f, 1f, 0.35f);
                case Element.Holy: return new Color(1f, 0.95f, 0.75f);
                case Element.Dark: return new Color(0.75f, 0.35f, 1f);
                default: return new Color(1f, 1f, 1f);
            }
        }

        /// <summary>
        /// Animates a batch of battle events. <paramref name="onEvent"/> fires as each lands
        /// (the UI shows numbers and text there).
        /// </summary>
        public IEnumerator Play(Battle b, List<BattleEvent> events, Action<BattleEvent> onEvent, Element castElement = Element.Physical)
        {
            int lastSource = -1;
            foreach (var e in events)
            {
                var src = e.SourceId >= 0 ? Actor(e.SourceId) : null;
                var dst = e.TargetId >= 0 ? Actor(e.TargetId) : null;

                if (src != null && e.SourceId != lastSource && (e.Kind == BattleEventKind.Damage || e.Kind == BattleEventKind.Heal || e.Kind == BattleEventKind.Info))
                {
                    lastSource = e.SourceId;
                    var srcUnit = b.Unit(e.SourceId);
                    if (dst != null && e.Kind == BattleEventKind.Damage && srcUnit != null && srcUnit.IsHero != b.Unit(e.TargetId).IsHero)
                        yield return src.Lunge(dst.transform.position);
                    else
                        yield return src.Hop(0.25f, 0.25f);
                }

                onEvent?.Invoke(e);
                switch (e.Kind)
                {
                    case BattleEventKind.Damage:
                        if (dst != null)
                        {
                            Burst(dst.CenterWorld, e.Weak ? ElementColor(castElement) : new Color(1, 0.9f, 0.8f), e.Crit ? 40 : 20);
                            StartCoroutine(dst.Flash(Color.white));
                            yield return dst.Shake(e.Crit ? 0.2f : 0.1f, 0.22f);
                        }
                        break;
                    case BattleEventKind.Heal:
                    case BattleEventKind.Revive:
                        if (dst != null)
                        {
                            if (e.Kind == BattleEventKind.Revive) dst.SetFallen(false);
                            Burst(dst.CenterWorld, new Color(0.5f, 1f, 0.6f), 25);
                            yield return dst.Flash(new Color(0.4f, 1f, 0.5f), 0.3f);
                        }
                        break;
                    case BattleEventKind.Break:
                        if (dst != null)
                        {
                            Burst(dst.CenterWorld, new Color(1f, 0.85f, 0.3f), 60);
                            yield return dst.Shake(0.3f, 0.45f);
                        }
                        break;
                    case BattleEventKind.Defeated:
                        if (dst != null)
                        {
                            var unit = b.Unit(e.TargetId);
                            if (unit != null && unit.IsHero) dst.SetFallen(true);
                            else yield return dst.Defeat();
                        }
                        break;
                    case BattleEventKind.Status:
                        if (dst != null) yield return dst.Flash(new Color(0.6f, 0.7f, 1f), 0.25f);
                        break;
                    default:
                        yield return new WaitForSeconds(0.12f);
                        break;
                }
                yield return new WaitForSeconds(0.08f);
            }
        }

        void Burst(Vector3 pos, Color c, int count)
        {
            _burst.transform.position = pos;
            var p = new ParticleSystem.EmitParams { startColor = c, applyShapeToPosition = true };
            _burst.Emit(p, count);
        }
    }
}
