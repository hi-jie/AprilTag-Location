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

### 1. App使用

   - 打开应用后点击"设置"按钮
   - 配置AprilTag家族类型（如tag16h5）
   - 设置四个角点标签ID（用于建立坐标系）
   - 设置前后标签ID（用于定位目标位置和角度）

### 2. 信息接收

1. 确保Android设备与PC处于同一个局域网内

2. 信息使用UDP协议进行传输，类型为纯文本，格式为 `x,y,angle`

3. 信息接收：

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
    例如: 0.12345,0.67890,90.50000
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.settimeout(2.0)  # 设置超时
    try:
        sock.bind((host, port))
        print(f"UDP接收器已启动，监听 {host}:{port}")
    except Exception as e:
        print(f"错误: {e}")
        return

    # 用于计算帧率
    frame_times = deque(maxlen=30)
    last_print_time = time.time()

    print("等待接收数据... (按 Ctrl+C 退出)")
    try:
        while True:
            try:
                # 接收数据
                data, addr = sock.recvfrom(1024)
                current_time = time.time()
                
                # 记录时间用于计算帧率
                frame_times.append(current_time)
                
                # 解析数据 (格式: x,y,angle)
                decoded_data = data.decode('utf-8').strip()
                values = decoded_data.split(',')
                
                if len(values) == 3:
                    x, y, angle = map(float, values)
                    print(f"[{addr[0]}:{addr[1]}] X:{x:.5f}, Y:{y:.5f}, Angle:{angle:.5f}°")
                else:
                    print(f"警告: 接收到格式错误的数据: {decoded_data}")
                
                # 每秒计算并输出一次帧率
                if current_time - last_print_time >= 1.0:
                    if len(frame_times) > 1:
                        time_window = frame_times[-1] - frame_times[0]
                        if time_window > 0:
                            fps = (len(frame_times) - 1) / time_window
                            print(f"接收帧率: {fps:.2f} FPS")
                    
                    last_print_time = current_time
            
            except socket.timeout:
                continue  # 继续等待数据
                
    except KeyboardInterrupt:
        print("\n接收器已停止")
    finally:
        sock.close()

if __name__ == "__main__":
    # 运行接收器
    udp_receiver(host='0.0.0.0', port=8080)
```

## 其他

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