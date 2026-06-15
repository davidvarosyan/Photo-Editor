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

**GPU-accelerated** via [GPUImage](https://github.com/cats-oss/android-gpuimage)
(OpenGL ES fragment shaders). All enabled filters run as a single GLES filter
group in one upload → multi-pass → readback, off-screen, off the main thread
([`GpuImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/GpuImageProcessor.kt)).
Each step is a [`GlImageFilter`](app/src/main/java/com/varos/imageenhance/data/filter/GlImageFilter.kt)
(domain metadata + a GPUImage filter factory), applied in registration order:

1. **Brightness** — `GPUImageBrightnessFilter`.
2. **Contrast** — `GPUImageContrastFilter`.
3. **Denoise** — edge-preserving `GPUImageBilateralBlurFilter`.
4. **Sharpen** — `GPUImageSharpenFilter`.
5. **Edges** — 3×3 Laplacian high-boost via `GPUImage3x3ConvolutionFilter`.
6. **Blur** — `GPUImageGaussianBlurFilter`.
7. **Grayscale** — `GPUImageGrayscaleFilter` (toggle).
8. **B&W** — `GPUImageLuminanceThresholdFilter` (one knob doubles as on/off).
9. **Document** — custom GLES adaptive threshold
   ([`GpuAdaptiveThresholdFilter`](app/src/main/java/com/varos/imageenhance/data/filter/GpuAdaptiveThresholdFilter.kt)):
   each pixel vs its 5×5 local mean, so uneven lighting binarizes cleanly (toggle).

Adding a filter is two steps (implement `GlImageFilter`, add it to the list in
`appModule`) — UI tabs, seek bars and the pipeline are all generic over the
interface. The domain `ImageFilter` stays pure (no GPU types); the GPU factory
lives in the data layer.

## Architecture

**Clean Architecture + MVI**, one-way data flow, a single immutable state, **use
cases** per action, and **Koin** for DI. Folders mirror the layers and the MVI
roles, so the structure is predictable:

```
domain/                     ← pure (no Android UI deps)
  model/                    ImageFilter, FilterParameter, PipelineSettings
  repository/               ImageRepository
  processor/                ImageProcessor
  usecase/                  LoadImage, EnhanceImage, SaveImageToGallery,
                            CreateCaptureUri, GetFilters  (one file each)
data/                       ← framework implementations
  filter/                   BrightnessFilter, ContrastFilter, SharpenFilter,
                            GrayscaleFilter, ThresholdFilter, ColorMatrixFilter
  processor/                FilterPipeline
  repository/               AndroidImageRepository
di/                         appModule  ← single Koin composition root
presentation/
  theme/                    Compose theme
  editor/                   EditorContract (State/Intent/Effect),
                            EditorViewModel, EditorScreen
    components/             BeforeAfterSlider, ToolPanel
```

### MVI

- **State** — `EditorState`, one immutable data class. The screen is a pure
  function of it.
- **Intent** — `EditorIntent` (sealed). The View only ever calls
  `viewModel.onIntent(...)`; the ViewModel is the single owner/mutator of state.
- **Effect** — `EditorEffect` (sealed), one-shot side effects (snackbar
  messages) delivered over a `Channel`, kept out of persistent state.

### Live rendering (responsive seek bars)

Because processing is GPU-accelerated, every seek-bar change renders the **full
(heap-bounded) working bitmap** directly — no separate downscaled-preview tier.
A single stream uses **`conflate()`**, which "processes only the latest": any
intermediate values that arrive while a render is in flight are dropped, and the
newest one renders to completion (never cancelled), so the displayed frame always
matches the finger.

### Pluggable rendering backend

The pipeline is **backend-agnostic**. The same domain, MVI ViewModel and use
cases drive either of two `ImageProcessor` implementations, chosen by one flag
(`USE_GPU`) in [`appModule`](app/src/main/java/com/varos/imageenhance/di/AppModule.kt):

- [`GpuImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/GpuImageProcessor.kt) — OpenGL ES via GPUImage (default, full filter set).
- [`CpuImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/CpuImageProcessor.kt) — CPU bitmap passes (a parallel backend kept in the codebase to demonstrate extensibility; a subset of filters, easily expanded).

Each filter declares pure domain metadata (`ImageFilter`) plus a backend-specific
renderer (`GlImageFilter` → a GPUImage filter; `CpuImageFilter` → a bitmap pass).
New backends (RenderEffect, native, remote…) drop in the same way.

### Process-death survival ("AKM")

The source `Uri`, the `PipelineSettings` and the selected tab are persisted in
**`SavedStateHandle`**. On recreation (config change *or* background process
kill) the ViewModel reloads the image from the `Uri` and re-applies the saved
edits, returning the user exactly where they were. Bitmaps are too large to
serialize, so we persist the `Uri` and reload rather than the pixels.

### Memory strategy — high-res but OOM-safe

Driven by [`MemoryPolicy`](app/src/main/java/com/varos/imageenhance/domain/policy/MemoryPolicy.kt),
which derives **pixel budgets from the heap** (`Runtime.maxMemory()`, with
`largeHeap=true`). Sampling is by total pixels (not edge), so the bound holds for
any aspect ratio including panoramas/gigapixel maps.

- **Editing** runs on a heap-bounded working copy (`editingMaxPixels`) — as high
  resolution as fits safely once the ~4× pipeline peak and live preview are
  accounted for. The live drag preview is further downscaled (~1080px).
- **Saving** re-decodes the *original* at `exportMaxPixels` and re-runs the
  pipeline ([`ExportImageUseCase`](app/src/main/java/com/varos/imageenhance/domain/usecase/ExportImageUseCase.kt)),
  so the file is **full original resolution whenever it fits the budget** (a
  typical 12 MP photo saves at its true 4000×3000). Only images larger than the
  budget — e.g. gigapixel maps — are downsampled to the largest safe size, so a
  huge image **never OOMs**; the save toast reports the actual saved dimensions.
- Pipeline intermediates are recycled as the chain runs; the export bitmap is
  recycled after compression.
- **Rotation** — EXIF orientation baked into the bitmap (best-effort; a
  missing/unreadable tag never fails the load).
- **DI with Koin** — `ImageEnhanceApp` starts Koin; `MainActivity` resolves the
  ViewModel via `koinViewModel()` (Koin supplies `SavedStateHandle`); the whole
  graph lives in one readable module.

## Third-party libraries

Intentionally minimal — no image library needed:

- **GPUImage** (`jp.co.cyberagent.android:gpuimage`) — OpenGL ES filter pipeline
  with off-screen bitmap rendering; gives GPU-accelerated convolutions/blur on
  all target devices (works on minSdk 28, no deprecated RenderScript/AGSL-33+).
- **Koin** (`koin-androidx-compose`) — lightweight, no-codegen DI; one readable
  composition root, which suits the plugin-filter model.
- **`androidx.exifinterface`** — reliable EXIF orientation across formats/devices.
- **`kotlinx-coroutines`** — background processing, debounce, cancellation.
- **`lifecycle-viewmodel-compose`** + **`material-icons-extended`** — standard
  Jetpack/Compose tooling. Bitmaps render directly via `bitmap.asImageBitmap()`,
  so no Coil/Glide dependency.

## Trade-offs (timebox)

- GPU max texture size is treated as 4096² (the safe API-28+ floor) rather than
  queried at runtime, so images above ~16 MP are downsampled even on GPUs that
  could handle more.
- GPUImage renders each `process()` call as a fresh off-screen pass; a persistent
  GL context with per-frame uniform updates would squeeze out more live-preview
  performance (a follow-up).
- Preview/final reload on process death rather than persisting pixels.

## What I'd improve for production

- Re-introduce optional heavier filters behind the same `ImageFilter` contract,
  GPU-accelerated where it pays off.
- Undo/redo stack; crop/rotate gestures in the viewport.
- Broader unit/instrumentation tests (Robolectric for the pipeline).

## Known limitations

- Sharpen/threshold are O(pixels) on CPU; the full-res render after a drag has a
  brief delay (shown via the progress bar) — the live preview hides it.
- Save targets `Pictures/ImageEnhance` via MediaStore; on API < 29 behaviour may
  vary by OEM.
- On process death the captured-photo temp file (camera path) may have been
  cleared from cache; gallery picks always reload.
- Truly enormous images (gigapixel) are saved downsampled to the heap-safe size,
  not at full original resolution — `Bitmap.compress` needs the whole bitmap in
  memory, so full-res export of those would require tiled encoding (a production
  follow-up). Normal phone photos (≤ ~16 MP) save at true original resolution.
- One image at a time; no batch processing.

## Build & run

Open in Android Studio and run the `app` configuration, or:

```
./gradlew :app:assembleDebug
```

Requires `compileSdk 36`, JDK 21, `minSdk 28`.
```
./gradlew test            # pure-domain unit tests
```
