# AI models hosted for Viper Vision

ONNX conversions of photo upscalers that are only published as PyTorch files. The editor's
**Upscale** AI bubble downloads them from this folder (by commit, so the address never changes)
the first time they're used. Converted with [spandrel](https://github.com/chaiNNer-org/spandrel)
and `torch.onnx.export` (opset 17, dynamic size, FP32), and checked against the original PyTorch
output (max difference ~3e-6).

| File | Original | Author | License |
|---|---|---|---|
| `4xPurePhoto-span.onnx` | [4xPurePhoto-span](https://openmodeldb.info/models/4x-PurePhoto-span) (SPAN) | asterixcool / starinspace | CC-BY-SA-4.0 |
| `4xNomosWebPhoto_RealPLKSR.onnx` | [4xNomosWebPhoto_RealPLKSR](https://openmodeldb.info/models/4x-NomosWebPhoto-RealPLKSR) (RealPLKSR) | Philip Hofmann (Phhofm) | CC-BY-4.0 |

The **Restore faces** bubble's CodeFormer choice downloads `codeformer.onnx` from
[FaceFusion's model assets](https://github.com/facefusion/facefusion-assets/releases/tag/models-3.0.0)
([CodeFormer](https://github.com/sczhou/CodeFormer), S-Lab License 1.0: non-commercial use).
