# AprilTag定位系统项目结构文档

## 项目概述

这是一个基于AprilTag的实时位置与姿态估计系统，包含Android端的图像采集与处理以及PC端的数据接收与解析。系统通过摄像头识别AprilTag标签，计算目标的位置(X,Y坐标)和角度，并通过UDP协议将数据发送到指定设备进行进一步处理或可视化展示。

## 项目整体结构

```
apriltag-location/
├── .git/                         # Git版本控制系统目录
├── .gradle/                      # Gradle构建系统缓存目录
├── .vscode/                      # VSCode编辑器配置目录
├── app/                          # Android应用模块
├── apriltag/                     # AprilTag原生库模块
├── gradle/                       # Gradle包装器目录
├── build.gradle.kts              # 顶级构建配置文件
├── gradle.properties             # Gradle全局属性配置
├── gradlew                       # Linux/Mac下的Gradle包装器脚本
├── gradlew.bat                   # Windows下的Gradle包装器脚本
├── local.properties              # 本地属性配置（NDK路径等）
├── settings.gradle.kts           # 项目设置文件
├── README.md                     # 项目说明文档
├── STRUCTURE.md                  # 项目结构文档
├── udp_receiver.py               # Python UDP接收程序示例
└── .gitignore                    # Git忽略文件配置
```

## app模块结构

```
app/
├── build/                        # 构建输出目录
├── build.gradle.kts              # app模块构建配置
├── libs/                         # 第三方库存放目录
└── src/
    └── main/
        ├── AndroidManifest.xml   # Android应用配置文件
        ├── java/                 # Java/Kotlin源代码
        │   ├── com/
        │   │   └── example/
        │   │       └── apriltaglocation/    # 主要应用功能包
        │   │           ├── MainActivity.java       # 主Activity，负责相机控制和UI交互
        │   │           ├── SettingsActivity.java   # 设置Activity，配置AprilTag参数和网络设置
        │   │           ├── AprilTagAnalyzer.java   # AprilTag分析器，处理图像分析和坐标计算
        │   │           ├── AprilTagDetector.java   # AprilTag检测器，核心检测逻辑实现
        │   │           ├── NetworkSender.java      # 网络发送器，UDP数据传输实现
        │   │           └── MyApplication.java      # Application类，应用入口
        │   └── edu/
        │       └── umich/
        │           └── eecs/
        │               └── april/
        │                   └── apriltag/
        │                       ├── ApriltagDetection.java      # AprilTag检测接口
        │                       └── ApriltagNative.java         # AprilTag原生库封装
        └── res/                  # 资源文件
            ├── layout/           # 布局XML文件
            │   ├── activity_main.xml          # 主界面布局
            │   └── activity_settings.xml      # 设置界面布局
            ├── mipmap-anydpi-v26/ # 自适应图标
            ├── values/           # 字符串、颜色、样式等资源
            └── xml/              # 其他XML资源文件
```

## apriltag模块结构

```
apriltag/
├── .cxx/                         # CMake构建缓存目录
├── .github/                      # GitHub相关文件
├── CMake/                        # CMake工具脚本
├── build/                        # 原生库构建输出目录
├── common/                       # AprilTag公共组件源码
├── example/                      # 示例程序
├── src/                          # 原生库源码目录
│   └── main/
│       └── cpp/                  # C++源码文件
├── test/                         # 测试文件
├── CMakeLists.txt                # CMake构建配置文件
├── build.gradle                  # 原生库模块构建配置
├── LICENSE.md                    # 许可证文件
├── README.md                     # AprilTag库说明文档
├── apriltag.c                    # AprilTag核心实现
├── apriltag.h                    # AprilTag头文件
├── apriltag_math.h               # AprilTag数学运算头文件
├── apriltag_pose.c               # AprilTag姿态估计实现
├── apriltag_pose.h               # AprilTag姿态估计头文件
├── apriltag_quad_thresh.c        # AprilTag四边形阈值实现
├── apriltag_pywrap.c             # Python包装实现
├── package.xml                   # 包描述文件
├── tag16h5.c/.h                  # tag16h5标签族实现
├── tag25h9.c/.h                  # tag25h9标签族实现
├── tag36h10.c/.h                 # tag36h10标签族实现
├── tag36h11.c/.h                 # tag36h11标签族实现
├── tagCircle21h7.c/.h            # tagCircle21h7标签族实现
├── tagCircle49h12.c/.h           # tagCircle49h12标签族实现
├── tagCustom48h12.c/.h           # tagCustom48h12标签族实现
├── tagStandard41h12.c/.h         # tagStandard41h12标签族实现
└── tagStandard52h13.c/.h         # tagStandard52h13标签族实现
```

## 关键文件说明

### 构建配置文件

- [build.gradle.kts](file:///d:/Files/zzj/Programs/android/apriltag-location/build.gradle.kts): 项目顶级构建配置，定义了Gradle插件版本
- [app/build.gradle.kts](file:///d:/Files/zzj/Programs/android/apriltag-location/app/build.gradle.kts): Android应用模块构建配置，包含CameraX、OkHttp等依赖
- [apriltag/build.gradle](file:///d:/Files/zzj/Programs/android/apriltag-location/apriltag/build.gradle): AprilTag原生库模块构建配置，配置了NDK和CMake构建
- [CMakeLists.txt](file:///d:/Files/zzj/Programs/android/apriltag-location/apriltag/CMakeLists.txt): CMake构建配置文件，定义了原生库的构建规则

### Android应用核心文件

- [AndroidManifest.xml](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/AndroidManifest.xml): 应用权限和组件声明，包括相机、网络权限
- [MainActivity.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/MainActivity.java): 主Activity，负责相机控制、UI交互和整体流程管理
- [SettingsActivity.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/SettingsActivity.java): 设置Activity，用于配置AprilTag参数和网络设置
- [AprilTagAnalyzer.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/AprilTagAnalyzer.java): AprilTag分析器，处理图像分析和坐标计算逻辑
- [AprilTagDetector.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/AprilTagDetector.java): AprilTag检测器，实现核心检测算法和透视变换
- [NetworkSender.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/NetworkSender.java): 网络发送器，实现UDP数据传输功能
- [MyApplication.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/com/example/apriltaglocation/MyApplication.java): Application类，应用生命周期管理
- [ApriltagDetection.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/edu/umich/eecs/april/apriltag/ApriltagDetection.java): AprilTag检测接口定义
- [ApriltagNative.java](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/java/edu/umich/eecs/april/apriltag/ApriltagNative.java): AprilTag原生库JNI封装

### 资源文件

- [activity_main.xml](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/res/layout/activity_main.xml): 主界面布局文件，包含相机预览和信息显示
- [activity_settings.xml](file:///d:/Files/zzj/Programs/android/apriltag-location/app/src/main/res/layout/activity_settings.xml): 设置界面布局文件，包含各种配置选项

### 工具脚本

- [udp_receiver.py](file:///d:/Files/zzj/Programs/android/apriltag-location/udp_receiver.py): Python UDP数据接收脚本，用于接收Android端发送的位置和角度数据

## 技术架构

1. **Android端**: 使用CameraX API捕获图像，集成Apriltag库进行标签检测
2. **原生库**: 基于AprilTag开源库的C/C++实现，支持多种标签族类
3. **网络传输**: 使用UDP协议进行数据传输
4. **标签族支持**: 支持多种AprilTag族类（tag16h5, tag25h9, tag36h11等）

## 主要功能

- 实时识别AprilTag标签
- 计算目标的二维位置(X,Y坐标)
- 计算目标的角度方向
- 实时帧率监控
- UDP数据传输
- 可配置的标签ID和类型

## 依赖关系

- CameraX: 用于相机图像采集
- OkHttp: 用于网络请求
- AprilTag库: 用于标签检测与姿态估计
- NDK: 用于原生C/C++代码编译
- CMake: 用于原生库构建系统

## 项目配置与构建说明

### 构建工具
- Gradle 8.2.2
- Android Gradle Plugin 8.2.2
- NDK版本 25.1.8937393
- CMake版本 3.18.1

### 最小SDK版本
- minSdk = 24
- targetSdk = 34
- compileSdk = 34

### CPU架构支持
- arm64-v8a (推荐)
- armeabi-v7a

### 项目构建命令
- `./gradlew build` - 构建整个项目
- `./gradlew assembleDebug` - 构建debug版本
- `./gradlew installDebug` - 安装debug版本到设备

## 内存与性能优化

根据AprilTag检测与图像处理性能优化规范，本项目采用以下优化策略：

1. 图像采集与处理优化：优先使用YUV格式的Y分量（灰度图）进行处理
2. 图像采集与显示一致性：统一使用9:16的宽高比
3. 运行时性能可视化：提供实时FPS显示
4. 移动设备性能优化：建议在移动设备上达到20帧以上的处理速度

## 安全规范

- 网络操作严格在后台线程执行，避免NetworkOnMainThreadException
- UI组件类型转换时进行类型检查，避免ClassCastException
- 数据精度保持一致性，浮点数统一保留5位小数

这个项目通过Android端进行图像采集和处理，然后通过UDP协议将计算结果发送到PC端或其他设备进行进一步处理或可视化展示。