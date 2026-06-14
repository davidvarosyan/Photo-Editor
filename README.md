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

Kept deliberately **light** (no GPU/RenderEffect, no heavy spatial passes). Each
step is a self-contained `ImageFilter` in
[`data/filter/`](app/src/main/java/com/varos/imageenhance/data/filter), applied
by the pipeline in registration order:

1. **Brightness** — additive `ColorMatrix` offset.
2. **Contrast** — multiplicative `ColorMatrix` pivoting around mid-gray so the
   image doesn't drift dark.
3. **Sharpen** — 3×3 unsharp-mask convolution.
4. **Grayscale** — `ColorMatrix` desaturation (toggle).
5. **B&W** — global luminance threshold (one knob doubles as on/off).

All passes are `suspend` and check `ensureActive()` per row so superseded renders
cancel promptly. Adding a filter is two steps (implement `ImageFilter`, add it to
the list in `appModule`) — UI tabs, seek bars and the pipeline are all generic
over the interface.

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

### Two-tier rendering (responsive seek bars)

Processing is split so the UI never blocks and the result is sharp:

- **While dragging** (`ChangeFilterValue`) we render a **downscaled preview**
  (`PREVIEW_MAX_EDGE`, ~1080 px) — fast, near-instant feedback every frame.
- **When the bar settles** (`CommitFilters`, fired from Slider
  `onValueChangeFinished`) we render the **full-resolution** result.

Both streams use `mapLatest`, so a newer value cancels stale work; the final
stream is debounced and shows a progress bar.

### Process-death survival ("AKM")

The source `Uri`, the `PipelineSettings` and the selected tab are persisted in
**`SavedStateHandle`**. On recreation (config change *or* background process
kill) the ViewModel reloads the image from the `Uri` and re-applies the saved
edits, returning the user exactly where they were. Bitmaps are too large to
serialize, so we persist the `Uri` and reload rather than the pixels.

### Other guarantees

- **Large images / memory** — decoded with `inSampleSize`, bounded to a 2048 px
  long edge; intermediates recycled in the pipeline.
- **Rotation** — EXIF orientation baked into the bitmap (best-effort; a
  missing/unreadable tag never fails the load).
- **DI with Koin** — `ImageEnhanceApp` starts Koin; `MainActivity` resolves the
  ViewModel via `koinViewModel()` (Koin supplies `SavedStateHandle`); the whole
  graph lives in one readable module.

## Third-party libraries

Intentionally minimal — no image library needed:

- **Koin** (`koin-androidx-compose`) — lightweight, no-codegen DI; one readable
  composition root, which suits the plugin-filter model.
- **`androidx.exifinterface`** — reliable EXIF orientation across formats/devices.
- **`kotlinx-coroutines`** — background processing, debounce, cancellation.
- **`lifecycle-viewmodel-compose`** + **`material-icons-extended`** — standard
  Jetpack/Compose tooling. Bitmaps render directly via `bitmap.asImageBitmap()`,
  so no Coil/Glide dependency.

## Trade-offs (timebox)

- Light filter set only (color-matrix + one convolution + threshold); heavier
  effects (blur, denoise, edge, adaptive document scan) and the GPU/AGSL pipeline
  were intentionally removed to keep the app simple and fast.
- Global (not adaptive) thresholding — predictable single knob.
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
