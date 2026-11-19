using UnityEngine;

namespace StepQuest.Unity.Art
{
    /// <summary>Per-region colours for the battle diorama, lighting and colour grading.</summary>
    public struct RegionPalette
    {
        public Color32 SkyTop, SkyBottom, Far, Mid, Ground, GroundAlt, Trunk, Foliage, FoliageLight, Rock, Accent;
        public Color KeyLight, AmbientLight, Fog;
        public string[] Props;
        public bool Fireflies;

        static Color32 H(string s) => PixelArt.Hex(s);
        static Color C(string s) => PixelArt.Hex(s);

        public static RegionPalette Of(string id)
        {
            switch (id)
            {
                case "forest":
                    return new RegionPalette { SkyTop = H("#1d3b3a"), SkyBottom = H("#6fa38a"), Far = H("#2f5a4a"), Mid = H("#24463a"), Ground = H("#3f5f2f"), GroundAlt = H("#4f7a3a"),
                        Trunk = H("#4a3324"), Foliage = H("#2f6a3a"), FoliageLight = H("#5fa24a"), Rock = H("#6a7064"), Accent = H("#d8ff8a"),
                        KeyLight = C("#ffe9b0"), AmbientLight = C("#3a5a4a"), Fog = C("#5a8a7a"), Props = new[] { "tree", "tree", "rock", "grass" }, Fireflies = true };
                case "desert":
                    return new RegionPalette { SkyTop = H("#e88a4a"), SkyBottom = H("#ffd9a0"), Far = H("#d8955a"), Mid = H("#c47a42"), Ground = H("#e3b56f"), GroundAlt = H("#d9a560"),
                        Trunk = H("#7a5a3a"), Foliage = H("#5a8a4a"), FoliageLight = H("#8ab86a"), Rock = H("#b07a50"), Accent = H("#ffe08a"),
                        KeyLight = C("#fff0c8"), AmbientLight = C("#a06a4a"), Fog = C("#f0c090"), Props = new[] { "cactus", "rock", "rock", "pillar" } };
                case "coast":
                    return new RegionPalette { SkyTop = H("#3a6aa8"), SkyBottom = H("#bfe6f0"), Far = H("#3a7ab0"), Mid = H("#2a5a8a"), Ground = H("#e8d8a8"), GroundAlt = H("#d8c490"),
                        Trunk = H("#8a6a4a"), Foliage = H("#3a8a5a"), FoliageLight = H("#6ac08a"), Rock = H("#7a8a94"), Accent = H("#bff6ff"),
                        KeyLight = C("#fff8e8"), AmbientLight = C("#4a6a8a"), Fog = C("#a8d0e0"), Props = new[] { "rock", "rock", "tree", "grass" } };
                case "peaks":
                    return new RegionPalette { SkyTop = H("#2a3a6a"), SkyBottom = H("#a8c0e0"), Far = H("#8aa0c8"), Mid = H("#5a6a8a"), Ground = H("#e8f0f8"), GroundAlt = H("#c8d8e8"),
                        Trunk = H("#4a3a3a"), Foliage = H("#2a4a4a"), FoliageLight = H("#e8f8ff"), Rock = H("#6a7a8a"), Accent = H("#8ae0ff"),
                        KeyLight = C("#e8f4ff"), AmbientLight = C("#4a5a7a"), Fog = C("#c8d8f0"), Props = new[] { "rock", "crystal", "tree", "rock" } };
                case "ruins":
                    return new RegionPalette { SkyTop = H("#140f2a"), SkyBottom = H("#5a3a7a"), Far = H("#3a2a5a"), Mid = H("#2a2040"), Ground = H("#5a5470"), GroundAlt = H("#6a6484"),
                        Trunk = H("#3a3048"), Foliage = H("#4a3a6a"), FoliageLight = H("#8a7ab0"), Rock = H("#8a84a0"), Accent = H("#ffd86b"),
                        KeyLight = C("#d8c8ff"), AmbientLight = C("#3a2a5a"), Fog = C("#5a4a7a"), Props = new[] { "pillar", "pillar", "crystal", "rock" }, Fireflies = true };
                default: // meadow
                    return new RegionPalette { SkyTop = H("#5aa0e0"), SkyBottom = H("#d8f0ff"), Far = H("#8ab8a0"), Mid = H("#5a9a5a"), Ground = H("#6aa84a"), GroundAlt = H("#7ab858"),
                        Trunk = H("#6a4a2a"), Foliage = H("#3f8a3a"), FoliageLight = H("#8ad05a"), Rock = H("#8a8a7a"), Accent = H("#fff08a"),
                        KeyLight = C("#fff4d8"), AmbientLight = C("#6a8aa0"), Fog = C("#cfe8f0"), Props = new[] { "tree", "rock", "grass", "grass" } };
            }
        }
    }
}
