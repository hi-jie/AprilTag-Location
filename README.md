# AprilTag定位系统

## 项目介绍

这是一个基于AprilTag的实时位置与姿态估计系统，包含Android端的图像采集与处理以及PC端的数据接收与解析。该系统能够通过摄像头识别AprilTag标签，并计算出目标的位置(X,Y坐标)和角度，然后通过UDP协议将数据发送到指定设备进行进一步处理或可视化展示。

### 主要功能
- 实时识别AprilTag标签
- 计算目标的二维位置(X,Y坐标)
- 计算目标的角度方向
- 实时帧率监控
- UDP数据传输
- 可配置的标签ID和类型

### 技术架构
- Android端使用CameraX API捕获图像
- 集成Apriltag库进行标签检测
- 使用UDP协议进行数据传输
- 支持多种AprilTag族类（tag16h5, tag25h9等）

## 使用教程

### 1. 项目构建教程

#### 前提条件
- Android Studio (推荐最新版本)
- Android SDK (API Level 21+)
- 设备支持相机权限

#### 构建步骤

1. 克隆项目到本地：
   ```bash
   git clone <项目地址>
   cd apriltag-location
   ```

2. 打开Android Studio，导入项目：
   - 启动Android Studio
   - 选择 "Open an existing project"
   - 导航到项目根目录并打开

3. 同步Gradle依赖：
   - Android Studio会自动提示同步项目依赖
   - 点击 "Sync Now" 或在菜单中选择 "File" → "Sync Project with Gradle Files"

4. 连接Android设备并启用开发者模式：
   - 启用USB调试
   - 授权当前电脑访问设备

5. 编译并安装应用：
   - 在Android Studio中点击绿色的运行按钮 ▶️
   - 或使用命令行：
     ```bash
     ./gradlew installDebug
     ```

6. 配置应用参数（首次运行）：
   - 打开应用后点击"设置"按钮
   - 配置AprilTag家族类型（如tag16h5）
   - 设置四个角点标签ID（用于建立坐标系）
   - 设置前后标签ID（用于定位目标位置和角度）
   - 配置UDP目标IP地址和端口

### 2. Python接收程序示例

项目自带了一个Python UDP接收程序，可以接收Android端发送的位置和角度数据：

#### 直接运行接收程序
```bash
python udp_receiver.py
```

#### 自定义Python接收程序示例

下面是一个简单的Python UDP接收程序示例，你可以根据自己的需求进行修改：

```python
import socket
import time
from collections import deque

def udp_receiver(host='0.0.0.0', port=8080):
    """
    接收Android端通过UDP发送的AprilTag位置和角度数据
    
    数据格式: x,y,angle
    例如: -0.123,0.456,90.5
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((host, port))
    print(f"UDP接收器已启动，监听 {host}:{port}")

    # 用于计算帧率
    frame_times = deque(maxlen=30)
    last_print_time = time.time()

    print("等待接收数据...")
    try:
        while True:
            # 接收数据
            data, addr = sock.recvfrom(1024)
            current_time = time.time()
            
            # 记录时间用于计算帧率
            frame_times.append(current_time)
            
            # 解析数据 (格式: x,y,angle)
            decoded_data = data.decode('utf-8').strip()
            values = decoded_data.split(',')
            
            x, y, angle = map(float, values)
            print(f"[{addr[0]}:{addr[1]}] X:{x:.3f}, Y:{y:.3f}, Angle:{angle:.1f}°")
            
            # 这里可以添加你的处理逻辑
            # 例如: 绘制轨迹、存储数据、触发其他操作等
            
            # 每秒计算并输出一次帧率
            if current_time - last_print_time >= 1.0:
                if len(frame_times) > 1:
                    time_window = frame_times[-1] - frame_times[0]
                    if time_window > 0:
                        fps = (len(frame_times) - 1) / time_window
                        print(f"接收帧率: {fps:.2f} FPS")
                
                last_print_time = current_time
                
    except KeyboardInterrupt:
        print("\n接收器已停止")
    finally:
        sock.close()

if __name__ == "__main__":
    # 可以修改主机和端口号
    udp_receiver(host='0.0.0.0', port=8080)
```

#### 使用说明

1. 确保Android设备和运行Python脚本的设备在同一个局域网内
2. 在Android应用中设置正确的IP地址和端口（默认是8080）
3. 先运行Python接收脚本，再启动Android应用
4. 观察控制台输出，应该能看到实时的位置和角度数据

### 应用场景

- 室内定位与导航
- 机器人定位与路径规划
- AR/VR空间定位
- 物体姿态估计
- 自动驾驶仿真测试

### 注意事项

- 确保AprilTag标签清晰可见且不过于倾斜
- 标签尺寸应适中，太小会导致识别困难
- 光线条件会影响识别效果，请确保照明充足
- 建议在稳定的网络环境下使用UDP传输