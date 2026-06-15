# Image Enhance

A small Android prototype that quickly improves the **readability** of an image —
receipts, documents, screenshots, notes, labels, or low-quality photos — by
running it through a configurable enhancement pipeline and showing a live
before/after comparison. All processing is on-device; nothing leaves the phone.

## Product problem

People constantly capture text-bearing images (a receipt for an expense report, a
whiteboard, a label, a page of notes) and the raw photo is often too dim, low
contrast, or noisy to read or archive. This feature gives a **one-tap "make it
readable"** path, plus a few sliders for the cases where one tap isn't enough.

## User flow

1. **Empty state** → "Choose from gallery" (modern Photo Picker, no storage
   permission needed) or "Take a photo" (system camera via `FileProvider`).
2. **Editor** → an interactive **before/after wipe slider** fills the top of the
   screen. Drag the handle to compare original vs. enhanced.
3. **Tool tabs** at the bottom — Brightness, Contrast, Denoise, Sharpen, Edges,
   Blur, Grayscale, Document — each tab exposes a single **seek bar** (or a toggle
   for the on/off operations). The controls panel is a **fixed height**, so the
   preview viewport keeps exactly the same size when you switch tabs (no reflow).
4. **Save** writes to the gallery (`Pictures/ImageEnhance`); **Reset** returns to
   the untouched original; camera/Replace swap the image; **Back** from the editor
   returns to the picker (it doesn't close the app).

A centered **circular spinner** appears while saving, and during processing only
if a render takes longer than 500 ms (so fast renders never flash a spinner).

I chose a **comparison slider** over a side-by-side view because readability is a
judgment the user makes by directly contrasting the two — the wipe makes that
immediate even on a phone screen. One-tool-per-tab keeps each adjustment focused
and the layout stable.

## Image-processing pipeline

GPU-accelerated with a **self-contained OpenGL ES 2.0 renderer** — no third-party
graphics library. The platform `EGL14`/`GLES20` are used directly:

- [`EglCore`](app/src/main/java/com/varos/imageenhance/data/processor/gl/EglCore.kt)
  sets up an off-screen EGL context (1×1 pbuffer, GLES2).
- [`GlImageRenderer`](app/src/main/java/com/varos/imageenhance/data/processor/gl/GlImageRenderer.kt)
  uploads the bitmap to a texture, runs the enabled filter passes through two
  **ping-pong FBOs**, and reads the result back with `glReadPixels`. The EGL
  context is persistent, shader **programs are compiled once and cached**, and FBO
  textures are reused (reallocated only when the image size changes).
- [`GlImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/GlImageProcessor.kt)
  drives it on one dedicated GL thread (EGL contexts are thread-bound).

Each filter is just **metadata + one GLSL fragment shader**
([`GlImageFilter`](app/src/main/java/com/varos/imageenhance/data/filter/gpu/GlImageFilter.kt)).
The renderer feeds every shader the same uniforms (`uTexture`, `uValue`,
`uTexelSize`), applied in registration order:

1. **Brightness** — additive (±0.5, shown as −100..100).
2. **Contrast** — scale around mid-gray (0.6..1.8).
3. **Denoise** — small bilateral filter (edge-preserving), blended by strength.
4. **Sharpen** — 3×3 unsharp mask.
5. **Edges** — Sobel gradient magnitude added back (high-boost).
6. **Blur** — box blur whose spread grows with the value.
7. **Grayscale** — luminance desaturation (toggle).
8. **Document** — adaptive threshold: each pixel vs its 5×5 local mean, so uneven
   lighting/shadows binarize cleanly for receipts/notes/pages (toggle).

Adding a filter is two steps: write a `GlImageFilter` (a shader string) and add it
to the list in `appModule`. UI tabs, seek bars and the pipeline are all generic
over the interface — no UI/ViewModel/state changes.

## Architecture

**Clean Architecture + MVI**, one-way data flow, a single immutable state, **use
cases** per action, and **Koin** for DI. Folders mirror the layers and MVI roles:

```
domain/                     ← pure (no Android UI deps)
  model/                    ImageFilter, FilterParameter, PipelineSettings
  policy/                   MemoryPolicy
  processor/                ImageProcessor
  repository/               ImageRepository
  usecase/                  LoadImage, EnhanceImage, SaveEditedImage,
                            SaveImageToGallery, CreateCaptureUri, GetFilters
data/                       ← framework implementations
  filter/gpu/               GlImageFilter + GLSL fragment-shader filters
  filter/cpu/               CpuImageFilter + CPU bitmap-pass filters
  processor/                GlImageProcessor, CpuImageProcessor
  processor/gl/             EglCore, GlImageRenderer  (the OpenGL ES renderer)
  repository/               AndroidImageRepository
di/                         appModule  ← single Koin composition root
presentation/
  theme/                    Compose theme
  editor/                   EditorContract (State/Intent/Effect),
                            EditorViewModel, EditorScreen
    components/             BeforeAfterSlider, ToolPanel
```

### MVI

- **State** — `EditorState`, one immutable data class; the screen is a pure
  function of it.
- **Intent** — `EditorIntent` (sealed). The View only ever calls
  `viewModel.onIntent(...)`; the ViewModel is the single owner of state.
- **Effect** — `EditorEffect` (sealed), one-shot side effects (snackbar messages)
  over a `Channel`, kept out of persistent state.
- **Reducer** — every mutation goes through one `reduce { it.copy(...) }` helper
  backed by `MutableStateFlow.update {}` (atomic compare-and-set), so the
  concurrent render/save/load coroutines never clobber each other. That's the
  functional `(EditorState) -> EditorState` seam.

### Live rendering (responsive seek bars)

Editing runs on a small working copy (see Memory) so the GPU re-renders it every
frame. A single stream uses **`conflate()`** — "process only the latest": values
that arrive while a render is in flight are dropped, and the newest renders to
completion (never cancelled), so the displayed frame always matches the finger.

### Pluggable rendering backend

The pipeline is **backend-agnostic**. The same domain, ViewModel and use cases
drive either of two `ImageProcessor` implementations, chosen by one flag
(`USE_GPU`) in [`appModule`](app/src/main/java/com/varos/imageenhance/di/AppModule.kt):

- [`GlImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/GlImageProcessor.kt) — our OpenGL ES renderer (default, full filter set).
- [`CpuImageProcessor`](app/src/main/java/com/varos/imageenhance/data/processor/CpuImageProcessor.kt) — CPU bitmap passes (a parallel backend kept in the codebase to prove the architecture is backend-agnostic; a subset of filters, easily expanded).

Each filter declares pure domain metadata (`ImageFilter`) plus a backend-specific
renderer (`GlImageFilter` → a fragment shader; `CpuImageFilter` → a bitmap pass).
New backends (RenderEffect, native, remote…) drop in the same way.

### Process-death survival ("AKM")

The source `Uri`, the `PipelineSettings`, the selected tab, **and the pending
camera-capture `Uri`** are persisted in **`SavedStateHandle`**. On recreation
(config change *or* background process kill) the ViewModel reloads the image and
re-applies the saved edits. The pending-capture key matters specifically for the
camera: under "don't keep activities" the process is killed while the camera app
is foreground, so the capture target must outlive the View — the `TakePicture`
result comes back as `EditorIntent.CameraCaptured` and the ViewModel reads the
target from saved state. Bitmaps are too large to serialize, so we persist `Uri`s
and reload.

### Memory & resolution strategy

Driven by [`MemoryPolicy`](app/src/main/java/com/varos/imageenhance/domain/policy/MemoryPolicy.kt).
Decoding samples by **total pixels** (not edge), so the bound holds for any aspect
ratio including panoramas/gigapixel maps, and never fails a load on EXIF issues
(orientation is baked in best-effort).

- **Editing size scales with the device.** `editingMaxPixels = heap/64`, clamped
  to **2.5 MP … 12 MP** (and ≤ one 4096² GL texture). A budget phone edits at the
  2.5 MP floor (stays smooth); a flagship goes up to ~12 MP. More heap ≈ stronger
  GPU, so capability and budget scale together.
- **Saving keeps the original resolution.** The exact on-screen result is
  upscaled (bilinear) back to the source image's pixel count and saved
  ([`SaveEditedImageUseCase`](app/src/main/java/com/varos/imageenhance/domain/usecase/SaveEditedImageUseCase.kt)),
  so the file matches the original's dimensions and is *visually identical to the
  preview*. The target is capped (`saveMaxPixels`, heap-guarded, ≤ 24 MP) so a
  gigapixel original is saved at the largest safe size rather than OOMing. The
  save toast reports the actual saved dimensions.
- **DI** — `ImageEnhanceApp` starts Koin; `MainActivity` resolves the ViewModel
  via `koinViewModel()` (Koin supplies `SavedStateHandle`); one readable module.

## Third-party libraries

Deliberately minimal — **no image or GL library**; rendering is hand-written on
`EGL14`/`GLES20`.

- **Koin** (`koin-androidx-compose`) — lightweight, no-codegen DI; one readable
  composition root, which suits the plugin-filter model.
- **`androidx.exifinterface`** — reliable EXIF orientation across formats/devices.
- **`kotlinx-coroutines`** — background processing, conflation, cancellation.
- **`lifecycle-viewmodel-compose`** + **`material-icons-extended`** — standard
  Jetpack/Compose tooling. Bitmaps render directly via `bitmap.asImageBitmap()`,
  so no Coil/Glide.

No native `.so` libraries are bundled (the app is 16 KB page-size compatible).

## Trade-offs (timebox)

- **Editing small, saving upscaled.** Editing happens on the small working copy
  for full responsiveness, and the saved file is that result upscaled to the
  original size — so it has the original *dimensions* but is softer than a true
  full-resolution re-render. The exchange buys a smooth 60fps drag. A "high
  quality save" that re-runs the pipeline on the full original could be offered
  as an option.
- GPU max texture size is assumed to be 4096² (the safe API-28+ floor) rather than
  queried, so very large images are downsampled even on GPUs that could do more.
- The GL `ImageProcessor` is an app-lifetime singleton and never explicitly
  destroys its GL resources / render thread (fine for one screen; production would
  scope it).

## What I'd improve for production

- Optional crisp full-resolution save (re-render the original, tiled for huge
  images).
- Two-pass separable blur and a larger denoise kernel for higher quality.
- Undo/redo stack; crop/rotate gestures in the viewport.
- Automated tests: unit-test `MemoryPolicy`/`PipelineSettings`, instrument the
  GL/CPU processors and process-death restoration.

## Known limitations

- Saved images are upscaled from the working copy — original dimensions, but not
  the crispness of a native full-res render.
- Save targets `Pictures/ImageEnhance` via MediaStore; on API < 29 behaviour may
  vary by OEM.
- Gigapixel originals are saved downsampled to the heap-safe size.
- The CPU backend implements a subset (Brightness, Contrast, Sharpen, Grayscale).
- One image at a time; no batch processing. No automated tests yet.

## Build & run

Open in Android Studio and run the `app` configuration, or:

```
./gradlew :app:assembleDebug
```

Requires `compileSdk 36`, JDK 21, `minSdk 28`. To run the whole pipeline on the
CPU instead of the GPU, flip `USE_GPU = false` in `di/AppModule.kt`.
