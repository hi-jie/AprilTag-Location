import socket
import time
from collections import deque

def udp_receiver(host='0.0.0.0', port=8080):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((host, port))
    print(f"UDP接收器已启动，监听 {host}:{port}")

    # 用于计算帧率
    frame_times = deque(maxlen=30)
    last_print_time = time.time()

    print("等待接收数据...")
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
        
        # 每秒计算并输出一次帧率
        if current_time - last_print_time >= 1.0:
            if len(frame_times) > 1:
                time_window = frame_times[-1] - frame_times[0]
                if time_window > 0:
                    fps = (len(frame_times) - 1) / time_window
                    print(f"接收帧率: {fps:.2f} FPS")
            
            last_print_time = current_time

if __name__ == "__main__":
    udp_receiver()