#define COMMON_API
#define FLOW_API
#define PARAMS_API
#define RENDER_API
#define JPEG_GLOBAL(type) type
//Assume all outside projects depending on JPEG are C++
#ifdef JPEG_EXPORTS
#define JPEG_EXTERN(type) extern type
#else
#define JPEG_EXTERN(type) extern "C" type
#endif //ifeq JPEG_EXPORTS

