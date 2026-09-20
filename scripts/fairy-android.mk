# 在上游 Makefile 之后加载，并直接构建 all，避免 build 的递归 make 丢失此覆盖。
# NDK r26 静态链接的 LTO 构造器可能排到 crtend 的空指针标记后面，导致全局对象未初始化。
CXXFLAGS := $(filter-out -flto,$(CXXFLAGS))
LDFLAGS := $(filter-out -flto -pie,$(LDFLAGS))
