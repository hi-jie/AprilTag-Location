import tkinter as tk
from tkinter import ttk
from PIL import Image, ImageTk
import math
import socket
import threading
import time
from collections import deque

position_label = "真实: X: {x:.5f}    Y: {y:.5f}    角度: {angle:.3f}°"
measurement_label = "测量: X: {x:.5f}    Y: {y:.5f}    角度: {angle:.3f}°"
error_label = "误差: X: {error_x:.5f}    Y: {error_y:.5f}    角度: {error_angle:.3f}°"
image_physical_width_label = "图片物理宽度: {image_physical_width:.3f} m"
FPS_label = "接收帧率: {fps:.2f} FPS"

class AprilTagLocationApp:
    def __init__(self, root):
        self.root = root
        self.root.title("AprilTag Location 误差分析工具")
        
        # 存储最近1秒的数据
        self.recent_data = deque()
        # 存储接收的UDP数据
        self.udp_data_history = deque()
        # 最大存储时间（秒）
        self.max_store_time = 1.0
        
        # 初始窗口大小
        self.initial_width = 800
        self.initial_height = 600

        self.norm_x = 0.0
        self.norm_y = 0.0
        self.angle = 0.0
        
        # 设置窗口大小
        self.root.geometry(f"{self.initial_width}x{self.initial_height}")
        
        # 固定比例因子
        self.canvas_aspect_ratio = 4/3  # 长宽比
        
        # 主容器，使用网格布局
        self.root.grid_rowconfigure(1, weight=1)  # 画布行可扩展
        self.root.grid_columnconfigure(0, weight=1)  # 唯一列可扩展
        
        # 控制面板 - 固定在顶部，大小和旋转滑块放在同一行
        control_frame = ttk.Frame(root)
        control_frame.grid(row=0, column=0, sticky="ew", padx=10, pady=10)
        control_frame.grid_columnconfigure(0, weight=1)  # 大小滑块列可扩展
        control_frame.grid_columnconfigure(1, weight=1)  # 旋转滑块列可扩展
        
        # 统一图片大小控制
        ttk.Label(control_frame, text="图片大小:").grid(row=0, column=0, sticky=tk.W, padx=(0, 5))
        self.image_size_var = tk.IntVar(value=50)
        image_size_scale = ttk.Scale(
            control_frame, from_=20, to=150, orient=tk.HORIZONTAL, 
            variable=self.image_size_var, command=self.on_image_size_change
        )
        image_size_scale.grid(row=1, column=0, sticky=tk.EW, padx=(0, 5))
        self.image_size_label = ttk.Label(control_frame, text=f"{self.image_size_var.get()}")
        self.image_size_label.grid(row=2, column=0, sticky=tk.W)
        
        # 旋转控制
        ttk.Label(control_frame, text="旋转角度:").grid(row=0, column=1, sticky=tk.W, padx=(5, 0))
        self.rotation_var = tk.DoubleVar(value=0)
        rotation_scale = ttk.Scale(
            control_frame, from_=0, to=360, orient=tk.HORIZONTAL,
            variable=self.rotation_var, command=self.on_rotation_change
        )
        rotation_scale.grid(row=1, column=1, sticky=tk.EW, padx=(5, 0))
        self.rotation_label = ttk.Label(control_frame, text=f"{self.rotation_var.get():.1f}°")
        self.rotation_label.grid(row=2, column=1, sticky=tk.W)
        
        # 画布区域 - 占据中间空间
        canvas_frame = ttk.Frame(root)
        canvas_frame.grid(row=1, column=0, sticky="nswe", padx=10, pady=(0, 10))
        canvas_frame.grid_rowconfigure(0, weight=1)
        canvas_frame.grid_columnconfigure(0, weight=1)
        
        # 创建外层画布容器，用于保持比例
        self.outer_canvas = tk.Canvas(canvas_frame, bg='white', highlightthickness=0)
        self.outer_canvas.grid(row=0, column=0, sticky="nsew")
        
        # 创建内层画布，用于绘制内容
        self.canvas = tk.Canvas(self.outer_canvas, bg='white', highlightthickness=0)
        self.canvas_id = self.outer_canvas.create_window(0, 0, window=self.canvas, anchor="nw")
        
        # 信息框架放在底部，整合位置信息和UDP数据信息
        info_frame = ttk.LabelFrame(root, text="信息显示")
        info_frame.grid(row=2, column=0, sticky="ew", padx=10, pady=(0, 10))

        info_frame.grid_columnconfigure(0, weight=2)
        info_frame.grid_columnconfigure(1, weight=1)
        
        # 三行信息显示
        self.position_label = ttk.Label(info_frame, text=position_label.format(x=0, y=0, angle=0, image_physical_width=0), anchor="w", font=("微软雅黑", 12))
        self.position_label.grid(row=0, column=0, padx=(5, 30), sticky="w")
        
        self.measurement_label = ttk.Label(info_frame, text=measurement_label.format(x=0, y=0, angle=0, fps=0), anchor="w", font=("微软雅黑", 12))
        self.measurement_label.grid(row=1, column=0, padx=(5, 30), sticky="w")
        
        self.error_label = ttk.Label(info_frame, text=error_label.format(error_x=0, error_y=0, error_angle=0), anchor="w", font=("微软雅黑", 12))
        self.error_label.grid(row=2, column=0, padx=(5, 30), sticky="w")
        
        self.image_physical_width_label = ttk.Label(info_frame, text=image_physical_width_label.format(image_physical_width=0), anchor="w", font=("微软雅黑", 12))
        self.image_physical_width_label.grid(row=0, column=1, sticky="w")

        self.fps_label = ttk.Label(info_frame, text=FPS_label.format(fps=0), anchor="w", font=("微软雅黑", 12))
        self.fps_label.grid(row=1, column=1, sticky="w")
        
        # 监听窗口大小变化事件
        self.root.bind('<Configure>', self.on_window_resize)
        
        # 加载图片
        self.load_images()
        
        # 立即调整画布大小以确保比例
        self.root.after(100, self.adjust_canvas_size)
        
        # 绘制初始布局
        self.draw_layout()
        
        # 设置拖动功能
        self.setup_drag_functionality()
        
        # 更新位置信息
        self.update_position_info()
        
        # 启动UDP接收器
        self.start_udp_receiver()
    
    def start_udp_receiver(self):
        """启动UDP接收器线程"""
        self.udp_thread = threading.Thread(target=self.run_udp_receiver, daemon=True)
        self.udp_thread.start()
    
    def run_udp_receiver(self):
        """运行UDP接收器"""
        host = '0.0.0.0'
        port = 8080
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(1.0)  # 1秒超时
        try:
            sock.bind((host, port))
            print(f"UDP接收器已启动，监听 {host}:{port}")
        except Exception as e:
            print(f"UDP绑定错误: {e}")
            return

        while True:
            try:
                # 接收数据
                data, addr = sock.recvfrom(1024)
                current_time = time.time()
                
                # 解析数据 (格式: x,y,angle,timestamp)
                decoded_data = data.decode('utf-8').strip()
                values = decoded_data.split(',')
                
                if len(values) == 4:
                    x, y, angle, timestamp = map(float, values)
                    # 添加接收时间戳
                    udp_data = {
                        'x': x,
                        'y': y,
                        'angle': angle,
                        'sent_timestamp': timestamp,
                        'received_timestamp': current_time
                    }
                    
                    # 添加到历史记录
                    self.udp_data_history.append(udp_data)
                    
                    # 移除超过最大存储时间的数据
                    while (self.udp_data_history and 
                           current_time - self.udp_data_history[0]['received_timestamp'] > self.max_store_time):
                        self.udp_data_history.popleft()
                    
                    # 在主线程中更新UDP信息显示
                    self.root.after(0, self.update_udp_info_display, udp_data)
                else:
                    print(f"警告: 接收到格式错误的数据: {decoded_data}")
            
            except socket.timeout:
                continue  # 继续等待数据
            except Exception as e:
                print(f"UDP接收错误: {e}")
                break

    def update_udp_info_display(self, udp_data):
        """更新UDP信息显示"""
        # 获取当前时间
        current_time = time.time()

        # 使用时间戳插值找到对应时间的真实数据
        interpolated_data = self.interpolate_at_time(udp_data['sent_timestamp'])
        
        if interpolated_data:
            # 计算误差
            pos_x_error = abs(self.norm_x - interpolated_data['x'])
            pos_y_error = abs(self.norm_y - interpolated_data['y'])
            angle_error = abs(self.angle - interpolated_data['angle'])
            if (angle_error > 180):
                angle_error = 360 - angle_error
            
            # 计算帧率（过去一秒内的平均帧率）
            recent_one_second = [d for d in self.udp_data_history 
                                if current_time - d['received_timestamp'] <= 1.0]
            fps = len(recent_one_second)
            
            # 更新测量值显示（第二行）
            self.measurement_label.config(
                text=measurement_label.format(
                    x=interpolated_data['x'], y=interpolated_data['y'], angle=interpolated_data['angle']
                )
            )
            self.fps_label.config(text=FPS_label.format(fps=fps))
            
            # 更新误差显示（第三行）
            self.error_label.config(
                text=error_label.format(error_x=pos_x_error, error_y=pos_y_error, error_angle=angle_error)
            )
        else:
            # 无插值数据时的显示
            self.measurement_label.config(
                text=measurement_label.format(x=0, y=0, angle=0, fps=0)
            )
            
            self.error_label.config(
                text=error_label.format(error_x=0, error_y=0, error_angle=0)
            )

    def interpolate_at_time(self, target_time):
        """根据时间戳插值获取对应时刻的数据"""
        if len(self.udp_data_history) < 2:
            return None

        # 找到最接近目标时间的两个数据点
        prev_data = None
        next_data = None

        for i in range(len(self.udp_data_history) - 1):
            if self.udp_data_history[i]['sent_timestamp'] <= target_time <= self.udp_data_history[i+1]['sent_timestamp']:
                prev_data = self.udp_data_history[i]
                next_data = self.udp_data_history[i+1]
                break

        # 如果没有找到合适的数据点，返回None
        if not prev_data or not next_data:
            return None

        # 计算插值系数
        t_diff = next_data['sent_timestamp'] - prev_data['sent_timestamp']
        if t_diff == 0:
            # 时间戳相同，直接返回其中一个
            return {
                'x': prev_data['x'],
                'y': prev_data['y'],
                'angle': prev_data['angle']
            }

        ratio = (target_time - prev_data['sent_timestamp']) / t_diff

        # 插值计算
        interpolated_x = prev_data['x'] + (next_data['x'] - prev_data['x']) * ratio
        interpolated_y = prev_data['y'] + (next_data['y'] - prev_data['y']) * ratio
        # 处理角度跨越0度/360度的问题
        angle_diff = next_data['angle'] - prev_data['angle']
        if angle_diff > 180:
            angle_diff -= 360
        elif angle_diff < -180:
            angle_diff += 360
        interpolated_angle = prev_data['angle'] + angle_diff * ratio

        return {
            'x': interpolated_x,
            'y': interpolated_y,
            'angle': interpolated_angle
        }

    def load_images(self):
        """加载所有图片"""
        # 加载角落图片
        self.corner_images = []
        for i in range(4):
            img = Image.open(f"img/{i}.png")
            self.corner_images.append(img)
        
        # 加载中心图片
        self.center_image = Image.open("img/4.png")
        
        # 保存原始图像用于缩放
        self.original_corner_images = [img.copy() for img in self.corner_images]
        self.original_center_image = self.center_image.copy()
        
        # 初始化显示图像
        self.resize_images()
    
    def resize_images(self):
        """根据滑块值调整图片大小"""
        size = self.image_size_var.get()
        
        # 调整所有图片大小（角落和中心）
        self.display_images = []
        for img in self.original_corner_images:
            resized = img.resize((size, size), Image.Resampling.LANCZOS)
            self.display_images.append(ImageTk.PhotoImage(resized))
        
        # 调整中心图片大小
        resized_center = self.original_center_image.resize(
            (size, size), Image.Resampling.LANCZOS
        )
        self.display_center_image = ImageTk.PhotoImage(resized_center)
        
        # 旋转后的图片也使用相同的大小
        self.rotated_center_image = self.display_center_image
    
    def draw_layout(self):
        """绘制整个布局"""
        # 获取当前画布大小
        canvas_width = self.canvas.winfo_width()
        canvas_height = self.canvas.winfo_height()
        
        if canvas_width <= 1 or canvas_height <= 1:
            # 避免初始化时的无效尺寸，使用默认值
            canvas_width = 600
            canvas_height = 450  # 保持4:3比例
        
        # 计算角落图片位置（以中心为准）
        margin = self.image_size_var.get() / 2 + 10
        positions = [
            (margin, margin),                           # 左上
            (canvas_width - margin, margin),           # 右上
            (margin, canvas_height - margin),          # 左下
            (canvas_width - margin, canvas_height - margin)  # 右下
        ]
        
        # 检查是否已有角落图片ID
        if not hasattr(self, 'corner_img_ids'):
            self.corner_img_ids = []
            # 创建角落图片
            for i, pos in enumerate(positions):
                img_id = self.canvas.create_image(pos[0], pos[1], 
                                                  image=self.display_images[i], anchor=tk.CENTER)
                self.corner_img_ids.append(img_id)
        else:
            # 更新现有角落图片的位置和图像
            for i, pos in enumerate(positions):
                self.canvas.coords(self.corner_img_ids[i], pos[0], pos[1])
                self.canvas.itemconfig(self.corner_img_ids[i], image=self.display_images[i])
        
        # 计算中心图片位置
        center_x = canvas_width / 2
        center_y = canvas_height / 2
        
        # 如果中心图片还未创建，则创建它
        if not hasattr(self, 'center_img_id'):
            self.center_img_id = self.canvas.create_image(center_x, center_y, 
                                                          image=self.rotated_center_image, anchor=tk.CENTER)
        else:
            # 更新现有中心图片的位置和图像
            self.canvas.coords(self.center_img_id, center_x, center_y)
            self.canvas.itemconfig(self.center_img_id, image=self.rotated_center_image)
        
        # 更新位置信息
        self.update_position_info()
    
    def rotate_center_image(self):
        """旋转中心图片，按照指定步骤进行旋转处理"""
        # 获取当前旋转角度
        angle = self.rotation_var.get()
        size = self.image_size_var.get()
        
        # 步骤1：使用数学方法计算图片旋转后的外接矩形大小
        # 将角度转换为弧度
        rad_angle = math.radians(angle)
        cos_val = abs(math.cos(rad_angle))
        sin_val = abs(math.sin(rad_angle))
        
        # 计算原始正方形图片旋转后的外接矩形尺寸
        # 对于边长为s的正方形，旋转后的外接矩形尺寸为 s*(|cos(θ)| + |sin(θ)|)
        rotated_bbox_size = int(size * (cos_val + sin_val))
        
        # 步骤2：将画布中的图片大小调整为此大小
        # 创建一个较大尺寸的透明背景图像以容纳旋转后的图片
        bbox_img = Image.new("RGBA", (rotated_bbox_size, rotated_bbox_size), (255, 255, 255, 0))
        
        # 步骤3：使用PIL进行会扩展的旋转，设置到画布中
        # 先放大原始图像以确保旋转后有足够的分辨率
        enlarged_img = self.original_center_image.resize((size, size), Image.Resampling.LANCZOS)
        
        # 使用expand=True进行旋转，使PIL自动扩展画布以适应旋转后的图像
        rotated_img = enlarged_img.rotate(-angle, expand=True)
        
        # 获取旋转后图像的实际尺寸
        rot_w, rot_h = rotated_img.size
        
        # 将旋转后的图像居中放置在预创建的透明背景上
        offset_x = (rotated_bbox_size - rot_w) // 2
        offset_y = (rotated_bbox_size - rot_h) // 2
        bbox_img.paste(rotated_img, (offset_x, offset_y))
        
        # 使用计算出的外接矩形大小的图像更新显示
        self.rotated_center_image = ImageTk.PhotoImage(bbox_img)
        
        # 更新画布上的图片
        self.canvas.itemconfig(self.center_img_id, image=self.rotated_center_image)
        
        # 更新位置信息
        self.update_position_info()
    
    def setup_drag_functionality(self):
        """设置拖拽功能"""
        self.dragging = False
        self.offset_x = 0
        self.offset_y = 0
        
        self.canvas.tag_bind(self.center_img_id, "<ButtonPress-1>", self.start_drag)
        self.canvas.tag_bind(self.center_img_id, "<B1-Motion>", self.on_drag)
        self.canvas.tag_bind(self.center_img_id, "<ButtonRelease-1>", self.end_drag)
    
    def start_drag(self, event):
        """开始拖拽"""
        self.dragging = True
        # 获取当前中心图片的位置
        x, y = self.canvas.coords(self.center_img_id)
        self.offset_x = event.x - x
        self.offset_y = event.y - y
    
    def on_drag(self, event):
        """拖拽过程中更新位置"""
        if self.dragging:
            x = event.x - self.offset_x
            y = event.y - self.offset_y
            self.canvas.coords(self.center_img_id, x, y)
            self.update_position_info()
    
    def end_drag(self, event):
        """结束拖拽"""
        self.dragging = False
    
    def on_image_size_change(self, value):
        """图片大小改变时的回调函数"""
        self.image_size_label.config(text=f"{int(float(value))}")
        self.resize_images()
        # 重新绘制整个布局
        self.draw_layout()
        self.setup_drag_functionality()  # 重新绑定事件
    
    def on_rotation_change(self, value):
        """旋转角度改变时的回调函数"""
        self.rotation_label.config(text=f"{float(value):.1f}°")
        self.rotate_center_image()
        self.update_position_info()
    
    def on_window_resize(self, event):
        """窗口大小改变时的回调函数"""
        # 只响应根窗口的大小改变事件
        if event.widget == self.root:
            # 使用延时调用确保窗口大小已更新
            self.root.after_idle(self.adjust_canvas_size)
    
    def adjust_canvas_size(self):
        """调整画布大小以保持4:3比例"""
        # 获取窗口的当前尺寸
        win_width = self.root.winfo_width()
        win_height = self.root.winfo_height()
        
        # 计算可用空间（减去其他组件占用的空间）
        control_height = 100  # 控制面板高度（包含两行）
        info_height = 120     # 信息面板高度（包含三行）
        padding = 30          # 总边距
        
        available_height = win_height - control_height - info_height - padding
        
        # 确保可用高度为正值
        if available_height <= 0:
            return
            
        # 获取canvas_frame的尺寸，这是画布的最大可用空间
        canvas_frame = self.outer_canvas.master
        canvas_frame.update_idletasks()  # 确保几何信息是最新的
        
        max_width = canvas_frame.winfo_width()
        max_height = canvas_frame.winfo_height()
        
        # 根据最大可用空间和4:3比例计算实际画布尺寸
        # 要保持4:3比例，width/height = 4/3 => width = 4*height/3 或 height = 3*width/4
        proposed_width_by_height = max_height * 4 / 3
        proposed_height_by_width = max_width * 3 / 4
        
        if proposed_width_by_height <= max_width:
            # 高度限制了尺寸，按高度计算宽度
            final_width = int(proposed_width_by_height)
            final_height = int(max_height)
        else:
            # 宽度限制了尺寸，按宽度计算高度
            final_width = int(max_width)
            final_height = int(proposed_height_by_width)
        
        # 设置外层画布大小
        self.outer_canvas.config(width=final_width, height=final_height)
        
        # 设置内层画布大小
        self.canvas.config(width=final_width, height=final_height)
        
        # 居中外层画布中的内层画布
        self.outer_canvas.coords(self.canvas_id, 
                                 (final_width - final_width) // 2, 
                                 (final_height - final_height) // 2)
        
        # 延迟重绘布局，避免频繁重绘
        if hasattr(self, 'resize_after_id'):
            self.root.after_cancel(self.resize_after_id)
        self.resize_after_id = self.root.after(50, self.draw_layout)
    
    def update_position_info(self):
        """更新位置和角度信息显示"""
        # 获取中心图片当前位置
        x, y = self.canvas.coords(self.center_img_id)
        
        # 计算参考点坐标 - 左上角和右下角图片的中心
        canvas_width = self.canvas.winfo_width()
        canvas_height = self.canvas.winfo_height()
        margin = self.image_size_var.get() / 2 + 10
        
        # 左上角图片中心坐标
        top_left_x = margin
        top_left_y = margin
        
        # 右下角图片中心坐标
        bottom_right_x = canvas_width - margin
        bottom_right_y = canvas_height - margin
        
        # 归一化坐标计算（相对于左上角到右下角的范围）
        # 新坐标系：左上角图片中心为(0,0)，右下角图片中心为(1,1)
        x_range = bottom_right_x - top_left_x
        y_range = bottom_right_y - top_left_y
        
        if x_range != 0:
            self.norm_x = (x - top_left_x) / x_range
        else:
            self.norm_x = 0
            
        if y_range != 0:
            self.norm_y = (y - top_left_y) / y_range
        else:
            self.norm_y = 0
        
        # 当前角度
        self.angle = self.rotation_var.get()

        # 计算图片的物理宽度（假设场地是4m×3m）
        canvas_width_meters = 4.0  # 场地宽度4米
        image_size_pixels = self.image_size_var.get()  # 图片像素大小
        canvas_width_pixels = self.canvas.winfo_width()  # 画布像素宽度
        
        if canvas_width_pixels > 0:
            # 计算每个像素代表的米数
            meters_per_pixel = canvas_width_meters / canvas_width_pixels
            # 图片的物理宽度（米）
            image_physical_width = image_size_pixels * meters_per_pixel
        else:
            image_physical_width = 0.0
        
        # 更新标签
        self.position_label.config(
            text=position_label.format(x=self.norm_x, y=self.norm_y, angle=self.angle)
        )
        self.image_physical_width_label.config(
            text=image_physical_width_label.format(image_physical_width=image_physical_width)
        )


def main():
    root = tk.Tk()
    app = AprilTagLocationApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()