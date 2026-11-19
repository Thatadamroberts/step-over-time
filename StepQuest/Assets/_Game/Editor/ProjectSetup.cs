using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.Rendering;
using UnityEngine.Rendering.Universal;
using UnityEngine.UIElements;

namespace StepQuest.EditorTools
{
    /// <summary>
    /// One-click project setup, run automatically the first time the project is opened
    /// (or via the StepQuest menu). Everything else in the game is built from code at runtime.
    /// </summary>
    [InitializeOnLoad]
    public static class ProjectSetup
    {
        const string ScenePath = "Assets/Scenes/Main.unity";
        const string SettingsDir = "Assets/Settings";
        const string MaterialsDir = "Assets/Resources/Materials";
        const string PanelSettingsPath = "Assets/Resources/UI/PanelSettings.asset";
        const string ThemePath = "Assets/Resources/UI/StepQuestTheme.tss";

        static ProjectSetup()
        {
            if (!File.Exists(ScenePath) || GraphicsSettings.defaultRenderPipeline == null)
                EditorApplication.delayCall += () => Run(false);
        }

        [MenuItem("StepQuest/Run Project Setup")]
        public static void RunFromMenu() => Run(true);

        static void Run(bool verbose)
        {
            if (EditorApplication.isPlayingOrWillChangePlaymode) return;
            EnsureFolder("Assets/Scenes");
            EnsureFolder(SettingsDir);
            EnsureFolder(MaterialsDir);
            SetupRenderPipeline();
            SetupMaterials();
            SetupPanelSettings();
            SetupScene();
            SetupPlayer();
            AssetDatabase.SaveAssets();
            AssetDatabase.Refresh();
            Debug.Log("StepQuest: project setup complete. Press Play, or File → Build Settings → Android → Build.");
            if (verbose) EditorUtility.DisplayDialog("StepQuest", "Project setup complete.\n\nPress Play to try it in the editor (debug step buttons are on), or build for Android.", "OK");
        }

        static void EnsureFolder(string path)
        {
            if (AssetDatabase.IsValidFolder(path)) return;
            var parent = Path.GetDirectoryName(path).Replace('\\', '/');
            EnsureFolder(parent);
            AssetDatabase.CreateFolder(parent, Path.GetFileName(path));
        }

        static void SetupRenderPipeline()
        {
            var asset = GraphicsSettings.defaultRenderPipeline as UniversalRenderPipelineAsset;
            if (asset == null)
            {
                var rendererPath = SettingsDir + "/StepQuest_Renderer.asset";
                var renderer = AssetDatabase.LoadAssetAtPath<UniversalRendererData>(rendererPath);
                if (renderer == null)
                {
                    renderer = ScriptableObject.CreateInstance<UniversalRendererData>();
                    AssetDatabase.CreateAsset(renderer, rendererPath);
                }
                var assetPath = SettingsDir + "/StepQuest_URP.asset";
                asset = AssetDatabase.LoadAssetAtPath<UniversalRenderPipelineAsset>(assetPath);
                if (asset == null)
                {
                    asset = UniversalRenderPipelineAsset.Create(renderer);
                    AssetDatabase.CreateAsset(asset, assetPath);
                }
                GraphicsSettings.defaultRenderPipeline = asset;
            }
            asset.supportsHDR = true;
            asset.shadowDistance = 40f;
            asset.msaaSampleCount = 1;
            asset.renderScale = 1f;
            EditorUtility.SetDirty(asset);
            for (int i = 0; i < QualitySettings.names.Length; i++)
            {
                QualitySettings.SetQualityLevel(i, false);
                QualitySettings.renderPipeline = asset;
            }
        }

        static void SetupMaterials()
        {
            // Lit, alpha-clipped, double-sided: pixel sprites that catch light and cast shadows.
            var lit = MakeMaterial("SpriteLit", "Universal Render Pipeline/Simple Lit");
            if (lit != null)
            {
                lit.SetFloat("_AlphaClip", 1f);
                lit.SetFloat("_Cutoff", 0.5f);
                lit.EnableKeyword("_ALPHATEST_ON");
                lit.SetFloat("_Cull", 0f);
                lit.SetFloat("_Smoothness", 0f);
                lit.SetFloat("_SpecularHighlights", 0f);
                lit.SetColor("_SpecColor", Color.black);
                lit.EnableKeyword("_EMISSION");
                lit.SetColor("_EmissionColor", Color.black);
                lit.globalIlluminationFlags = MaterialGlobalIlluminationFlags.EmissiveIsBlack;
                EditorUtility.SetDirty(lit);
            }

            var unlit = MakeMaterial("Unlit", "Universal Render Pipeline/Unlit");
            if (unlit != null)
            {
                unlit.SetFloat("_AlphaClip", 1f);
                unlit.SetFloat("_Cutoff", 0.5f);
                unlit.EnableKeyword("_ALPHATEST_ON");
                unlit.SetFloat("_Cull", 0f);
                EditorUtility.SetDirty(unlit);
            }

            // Additive particles for fireflies, dust and hit sparks.
            var particle = MakeMaterial("Particle", "Universal Render Pipeline/Particles/Unlit");
            if (particle != null)
            {
                particle.SetFloat("_Surface", 1f);
                particle.SetFloat("_Blend", 2f);
                particle.SetFloat("_SrcBlend", (float)BlendMode.SrcAlpha);
                particle.SetFloat("_DstBlend", (float)BlendMode.One);
                particle.SetFloat("_ZWrite", 0f);
                particle.EnableKeyword("_SURFACE_TYPE_TRANSPARENT");
                particle.renderQueue = (int)RenderQueue.Transparent;
                EditorUtility.SetDirty(particle);
            }
        }

        static Material MakeMaterial(string name, string shaderName)
        {
            var path = $"{MaterialsDir}/{name}.mat";
            var m = AssetDatabase.LoadAssetAtPath<Material>(path);
            if (m != null) return m;
            var shader = Shader.Find(shaderName);
            if (shader == null) { Debug.LogError($"StepQuest: shader '{shaderName}' not found. Is URP installed?"); return null; }
            m = new Material(shader) { name = name };
            AssetDatabase.CreateAsset(m, path);
            return m;
        }

        static void SetupPanelSettings()
        {
            var ps = AssetDatabase.LoadAssetAtPath<PanelSettings>(PanelSettingsPath);
            if (ps == null)
            {
                ps = ScriptableObject.CreateInstance<PanelSettings>();
                AssetDatabase.CreateAsset(ps, PanelSettingsPath);
            }
            ps.scaleMode = PanelScaleMode.ScaleWithScreenSize;
            ps.referenceResolution = new Vector2Int(1080, 1920);
            ps.screenMatchMode = PanelScreenMatchMode.MatchWidthOrHeight;
            ps.match = 0f;
            AssetDatabase.ImportAsset(ThemePath);
            var theme = AssetDatabase.LoadAssetAtPath<ThemeStyleSheet>(ThemePath);
            if (theme != null) ps.themeStyleSheet = theme;
            EditorUtility.SetDirty(ps);
        }

        static void SetupScene()
        {
            if (!File.Exists(ScenePath))
            {
                var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
                EditorSceneManager.SaveScene(scene, ScenePath);
            }
            EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        }

        static void SetupPlayer()
        {
            PlayerSettings.companyName = "StepQuest";
            PlayerSettings.productName = "StepQuest";
            PlayerSettings.colorSpace = ColorSpace.Linear;
            PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
            PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.stepquest.game");
            PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
            PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
            PlayerSettings.Android.minSdkVersion = AndroidSdkVersions.AndroidApiLevel28;
            PlayerSettings.Android.targetSdkVersion = (AndroidSdkVersions)35;
        }
    }

    /// <summary>Pixel art in Resources/Art imports crisp: point filtering, no compression, no mips.</summary>
    public sealed class PixelArtImporter : AssetPostprocessor
    {
        void OnPreprocessTexture()
        {
            if (!assetPath.Replace('\\', '/').Contains("/Resources/Art/")) return;
            var ti = (TextureImporter)assetImporter;
            ti.textureType = TextureImporterType.Default;
            ti.filterMode = FilterMode.Point;
            ti.textureCompression = TextureImporterCompression.Uncompressed;
            ti.mipmapEnabled = false;
            ti.alphaIsTransparency = true;
            ti.npotScale = TextureImporterNPOTScale.None;
            ti.wrapMode = TextureWrapMode.Clamp;
        }
    }
}
