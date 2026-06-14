# Image Enhance

A small Android prototype that quickly improves the **readability** of an image —
receipts, documents, screenshots, notes, labels, or low-quality photos — by
running it through a configurable enhancement pipeline and showing a live
before/after comparison.

## Product problem

People constantly capture text-bearing images (a receipt for an expense report, a
whiteboard, a label, a page of notes) and the raw photo is often too dim, low
contrast, or noisy to read or archive. This feature gives a **one-tap "make it
readable"** path, plus a few sliders for the cases where one tap isn't enough —
without sending anything to a server.

## User flow

1. **Empty state** → "Choose from gallery" (modern Photo Picker, no storage
   permission needed) or "Take a photo" (system camera via `FileProvider`).
2. **Editor** → an interactive **before/after wipe slider** fills the top of the
   screen. Drag the handle to compare original vs. enhanced.
3. **Tool tabs** at the bottom — Brightness, Contrast, Sharpen, Grayscale, B&W —
   each tab exposes a single **seek bar** (plus a toggle for the on/off
   operations). The controls panel is a **fixed height**, so the preview
   viewport keeps exactly the same size when you switch tabs (no jump/reflow).
4. **Save** writes the result to the gallery (`Pictures/ImageEnhance`); **Reset**
   returns to the untouched original; the camera/Replace actions swap the image.

I chose a **comparison slider** over a side-by-side view because readability is a
judgment the user makes by directly contrasting the two — the wipe makes that
immediate even on a phone screen. One-tool-per-tab keeps each adjustment focused
and the layout stable.

## Image-processing pipeline

Each step is a self-contained `ImageFilter` in
[`data/filter/`](app/src/main/java/com/varos/imageenhance/data/filter), applied
by the pipeline in registration order:

1. **Brightness** — additive `ColorMatrix` offset.
2. **Contrast** — multiplicative `ColorMatrix` pivoting around mid-gray so the
   image doesn't drift dark.
3. **Denoise** — 3×3 median filter (edge-preserving speckle removal), blended by
   strength.
4. **Sharpen** — 3×3 unsharp-mask convolution.
5. **Edges** — Sobel gradient magnitude high-boost to emphasize outlines/text.
6. **Blur** — separable O(pixels) sliding-window box blur (radius).
7. **Grayscale** — `ColorMatrix` desaturation (toggle).
8. **B&W** — global luminance threshold (one knob doubles as on/off).
9. **Document** — Bradley **adaptive** thresholding via an integral image: each
   pixel is compared to its local neighbourhood mean, so uneven lighting and
   shadows on receipts/notes/pages binarize cleanly (toggle).

All pixel passes are `suspend` and check `ensureActive()` per row so superseded
previews cancel promptly.

Each adjustable parameter is a field of the immutable
[`EnhancementSettings`](app/src/main/java/com/varos/imageenhance/domain/model/EnhancementSettings.kt).
Adding a new filter is: add a field, add a step in the processor, and add an
entry to the `EditTool` enum (one tab + seek bar) — the UI/state plumbing is
generic.

## Architecture

Clean Architecture + MVVM, one-way data flow with a single immutable UI state,
**use cases** per user action, and **Koin** for dependency injection.

```
domain/   ImageProcessor, ImageRepository (interfaces),
          ImageFilter / FilterParameter / PipelineSettings (models),
          usecase/ (LoadImage, EnhanceImage, SaveImage, CreateCaptureUri, GetFilters)
data/     AndroidImageRepository, FilterPipeline,
          filter/ (BrightnessFilter, ContrastFilter, SharpenFilter,
                   GrayscaleFilter, ThresholdFilter, ColorMatrixFilter base)
di/       appModule  ← single Koin composition root
ui/       EditorViewModel + EditorUiState + Compose screen/components
```

- **Pluggable filters**: every image-processing functional implements the
  [`ImageFilter`](app/src/main/java/com/varos/imageenhance/domain/model/ImageFilter.kt)
  interface (id, display name, one `FilterParameter`, `apply`). The pipeline, the
  tabs and the seek bars are all generic over this interface.
- **Adding a new functional** is two steps: implement `ImageFilter`, then add it
  to the `filters` list in
  [`appModule`](app/src/main/java/com/varos/imageenhance/di/AppModule.kt). It then
  automatically appears as a tab with its control and joins the pipeline — no UI,
  ViewModel, or state changes. List order = pipeline order = tab order.
- **Use cases** give one explicit, testable seam per action; the ViewModel knows
  nothing about repositories or the processor.
- **DI with Koin**: `App` starts Koin; `MainActivity` resolves the ViewModel via
  `koinViewModel()`; the graph is wired in one module.
- **UI ↔ state ↔ processing are separate**: the screen is a pure function of
  `EditorUiState`; the ViewModel mutates one `StateFlow`; the pixel work lives
  behind the `ImageProcessor` / `ImageFilter` interfaces.
- **Responsiveness / background work**: every slider change updates state
  instantly so the UI feels live, while the expensive processing runs on
  `Dispatchers.Default` driven by a **debounced** flow using `mapLatest`, so a
  newer parameter value **cancels** in-flight processing of a stale one. The
  pipeline checks `ensureActive()` between/within steps.
- **Large images / memory**: photos are decoded with `inSampleSize` and bounded
  to a max long-edge (2048 px), so we never inflate a 50 MP image into RAM.
- **Rotation**: EXIF orientation is read and baked into the bitmap (best-effort —
  a missing/unreadable EXIF tag never fails the load).
- **DI**: wired by hand in `MainActivity` (clearer than a framework for one
  screen); the interfaces keep the door open for Hilt later.

## Third-party libraries

Intentionally minimal — no image library was needed:

- **`androidx.exifinterface`** — reliable EXIF orientation across formats/devices.
- **`kotlinx-coroutines`** — background processing, debounce, cancellation.
- **Koin** (`koin-androidx-compose`) — lightweight, no-codegen DI; keeps the
  composition root in one readable module, which suits the plugin-filter model
  (registering a filter is a one-line list edit).
- **`lifecycle-viewmodel-compose`** + **`material-icons-extended`** — standard
  Jetpack/Compose tooling. Bitmaps are rendered directly via
  `bitmap.asImageBitmap()`, so no Coil/Glide dependency.

## Trade-offs (timebox)

- CPU convolution for sharpening rather than RenderScript/GPU/RenderEffect —
  simpler and portable; fine at the 2048 px preview size.
- Global (not adaptive) thresholding — one slider is predictable; adaptive
  (Sauvola/Otsu) would read better on uneven lighting but is more code.
- Manual DI instead of Hilt; minimal tests (pure-domain unit tests only).
- The processed result is held in memory; no undo history or document
  edge-detection/perspective-crop.

## What I'd improve for production

- Adaptive thresholding + auto-deskew/perspective crop for documents.
- GPU pipeline (`RenderEffect` / RenderScript replacement / OpenGL) for instant,
  full-resolution previews; process full-res only on save.
- Undo/redo stack and persisted edit sessions (`SavedStateHandle`).
- Hilt for DI, broader unit/instrumentation tests (Robolectric for the processor),
  and crop/rotate gestures in the viewport.

## Known limitations

- Sharpening/threshold are O(pixels) on CPU; very large previews have a brief
  processing delay (debounced + shown via a progress bar).
- Save targets `Pictures/ImageEnhance` via MediaStore; on API < 29 it relies on
  scoped behavior and may vary by OEM.
- No batch processing; one image at a time.

## Build & run

Open in Android Studio and run the `app` configuration, or:

```
./gradlew :app:assembleDebug
```

Requires `compileSdk 36`, JDK 21, `minSdk 28`.
```
./gradlew test            # pure-domain unit tests
```
