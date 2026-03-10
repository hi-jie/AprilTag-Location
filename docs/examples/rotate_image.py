import os
from PIL import Image

def rotate_images_in_directory(directory_path, angle=180):
    """
    将指定目录下的所有图片旋转指定角度并覆盖原图片
    
    :param directory_path: 包含图片的目录路径
    :param angle: 旋转角度，默认180度
    """
    # 支持的图片格式
    supported_formats = ('.png', '.jpg', '.jpeg', '.gif', '.bmp', '.tiff', '.webp')
    
    # 获取目录下所有文件
    for filename in os.listdir(directory_path):
        # 检查是否为支持的图片格式
        if filename.lower().endswith(supported_formats):
            filepath = os.path.join(directory_path, filename)
            
            try:
                # 打开图片
                with Image.open(filepath) as img:
                    # 旋转图片
                    rotated_img = img.rotate(angle, expand=False)
                    
                    # 保存并覆盖原图片
                    rotated_img.save(filepath)
                    print(f"已旋转 {filepath} 并保存")
                    
            except Exception as e:
                print(f"处理图片 {filepath} 时出错: {e}")

if __name__ == "__main__":
    img_directory = "./img"
    
    if os.path.exists(img_directory):
        print(f"开始旋转 {img_directory} 目录下的所有图片...")
        rotate_images_in_directory(img_directory)
        print("所有图片旋转完成！")
    else:
        print(f"目录 {img_directory} 不存在")