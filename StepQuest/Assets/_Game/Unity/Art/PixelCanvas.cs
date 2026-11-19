using UnityEngine;

namespace StepQuest.Unity.Art
{
    /// <summary>Tiny raster for drawing pixel art in code. (0,0) is the top-left.</summary>
    public sealed class PixelCanvas
    {
        public readonly int W, H;
        readonly Color32[] _px;
        static readonly Color32 Clear = new Color32(0, 0, 0, 0);

        public PixelCanvas(int w, int h) { W = w; H = h; _px = new Color32[w * h]; }

        /// <summary>Raw pixels, row-major from the top-left.</summary>
        public Color32[] Pixels => _px;

        public bool In(int x, int y) => x >= 0 && y >= 0 && x < W && y < H;
        public Color32 Get(int x, int y) => In(x, y) ? _px[y * W + x] : Clear;
        public bool Filled(int x, int y) => Get(x, y).a > 0;

        public void Set(int x, int y, Color32 c) { if (In(x, y)) _px[y * W + x] = c; }

        public void Rect(int x, int y, int w, int h, Color32 c)
        {
            for (int j = y; j < y + h; j++) for (int i = x; i < x + w; i++) Set(i, j, c);
        }

        public void Ellipse(float cx, float cy, float rx, float ry, Color32 c)
        {
            for (int j = Mathf.FloorToInt(cy - ry); j <= Mathf.CeilToInt(cy + ry); j++)
                for (int i = Mathf.FloorToInt(cx - rx); i <= Mathf.CeilToInt(cx + rx); i++)
                {
                    float dx = (i + 0.5f - cx) / rx, dy = (j + 0.5f - cy) / ry;
                    if (dx * dx + dy * dy <= 1f) Set(i, j, c);
                }
        }

        public void Circle(float cx, float cy, float r, Color32 c) => Ellipse(cx, cy, r, r, c);

        public void Line(int x0, int y0, int x1, int y1, Color32 c, int thickness = 1)
        {
            int dx = Mathf.Abs(x1 - x0), dy = -Mathf.Abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true)
            {
                for (int t = 0; t < thickness; t++) for (int u = 0; u < thickness; u++) Set(x0 + t, y0 + u, c);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 >= dy) { err += dy; x0 += sx; }
                if (e2 <= dx) { err += dx; y0 += sy; }
            }
        }

        /// <summary>Filled triangle (scanline).</summary>
        public void Tri(Vector2 a, Vector2 b, Vector2 c, Color32 col)
        {
            int minY = Mathf.FloorToInt(Mathf.Min(a.y, Mathf.Min(b.y, c.y)));
            int maxY = Mathf.CeilToInt(Mathf.Max(a.y, Mathf.Max(b.y, c.y)));
            int minX = Mathf.FloorToInt(Mathf.Min(a.x, Mathf.Min(b.x, c.x)));
            int maxX = Mathf.CeilToInt(Mathf.Max(a.x, Mathf.Max(b.x, c.x)));
            for (int y = minY; y <= maxY; y++)
                for (int x = minX; x <= maxX; x++)
                {
                    var p = new Vector2(x + 0.5f, y + 0.5f);
                    float d1 = Sign(p, a, b), d2 = Sign(p, b, c), d3 = Sign(p, c, a);
                    bool neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
                    if (!(neg && pos)) Set(x, y, col);
                }
        }

        static float Sign(Vector2 p1, Vector2 p2, Vector2 p3) => (p1.x - p3.x) * (p2.y - p3.y) - (p2.x - p3.x) * (p1.y - p3.y);

        public void MirrorX()
        {
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W / 2; x++)
                {
                    var a = _px[y * W + x]; _px[y * W + x] = _px[y * W + (W - 1 - x)]; _px[y * W + (W - 1 - x)] = a;
                }
        }

        /// <summary>
        /// The "HD pixel" pass: top-left rim highlights, bottom-right shading, and a dark tinted outline.
        /// </summary>
        public void Finish(bool outline = true, float light = 0.18f, float shade = 0.28f)
        {
            var src = (Color32[])_px.Clone();
            bool F(int x, int y) => x >= 0 && y >= 0 && x < W && y < H && src[y * W + x].a > 0;
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    if (!F(x, y)) continue;
                    var c = src[y * W + x];
                    if (c.a == 254) continue; // marked "emissive/no shading"
                    bool top = !F(x, y - 1) || !Same(src, x, y, x, y - 1), left = !F(x - 1, y) || !Same(src, x, y, x - 1, y);
                    bool bottom = !F(x, y + 1) || !Same(src, x, y, x, y + 1), right = !F(x + 1, y) || !Same(src, x, y, x + 1, y);
                    if (top && !bottom) _px[y * W + x] = Lerp(c, new Color32(255, 250, 230, 255), light);
                    else if (left && !right) _px[y * W + x] = Lerp(c, new Color32(255, 250, 230, 255), light * 0.6f);
                    else if (bottom || right) _px[y * W + x] = Lerp(c, new Color32(20, 10, 30, 255), shade);
                }
            if (!outline) return;
            var shaded = (Color32[])_px.Clone();
            for (int y = 0; y < H; y++)
                for (int x = 0; x < W; x++)
                {
                    if (F(x, y)) continue;
                    Color32? n = null;
                    if (F(x + 1, y)) n = src[y * W + x + 1];
                    else if (F(x - 1, y)) n = src[y * W + x - 1];
                    else if (F(x, y + 1)) n = src[(y + 1) * W + x];
                    else if (F(x, y - 1)) n = src[(y - 1) * W + x];
                    if (n.HasValue) shaded[y * W + x] = Lerp(n.Value, new Color32(14, 8, 20, 255), 0.75f);
                }
            System.Array.Copy(shaded, _px, _px.Length);
            for (int i = 0; i < _px.Length; i++) if (_px[i].a == 254) _px[i].a = 255;
        }

        bool Same(Color32[] s, int x0, int y0, int x1, int y1)
        {
            var a = s[y0 * W + x0]; var b = s[y1 * W + x1];
            return a.r == b.r && a.g == b.g && a.b == b.b;
        }

        public static Color32 Lerp(Color32 a, Color32 b, float t) => Color32.Lerp(a, b, t);

        public Texture2D ToTexture(string name)
        {
            var tex = new Texture2D(W, H, TextureFormat.RGBA32, false) { name = name, filterMode = FilterMode.Point, wrapMode = TextureWrapMode.Clamp };
            var flipped = new Color32[_px.Length];
            for (int y = 0; y < H; y++) System.Array.Copy(_px, y * W, flipped, (H - 1 - y) * W, W);
            tex.SetPixels32(flipped);
            tex.Apply(false, true);
            return tex;
        }
    }
}
