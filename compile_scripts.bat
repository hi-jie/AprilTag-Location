@echo off
echo AprilTag UDP Receiver 编译脚本
echo.

echo 检查编译器...
echo.

REM 检查GCC/G++是否存在
gcc --version >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 使用 GCC 编译器...
    echo 编译 C 版本...
    gcc "udp_receiver_c.c" -o "udp_receiver_c.exe" -lm
    if %ERRORLEVEL% EQU 0 (
        echo C 版本编译成功
    ) else (
        echo C 版本编译失败
    )
    
    echo 编译 C++ 版本...
    g++ "udp_receiver_cpp.cpp" -o "udp_receiver_cpp.exe"
    if %ERRORLEVEL% EQU 0 (
        echo C++ 版本编译成功
    ) else (
        echo C++ 版本编译失败
    )
) else (
    echo 未找到 GCC/G++ 编译器
    echo 请安装 MinGW-w64 或 TDM-GCC 并添加到 PATH 环境变量
    echo.
)

REM 检查 Visual Studio 编译器是否存在
cl >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo 使用 MSVC 编译器...
    echo 编译 C 版本...
    cl /Fe:udp_receiver_c_vs.exe "udp_receiver_c.c" ws2_32.lib
    if %ERRORLEVEL% EQU 0 (
        echo C 版本编译成功
    ) else (
        echo C 版本编译失败
    )
    
    echo 编译 C++ 版本...
    cl /EHsc /Fe:udp_receiver_cpp_vs.exe "udp_receiver_cpp.cpp" ws2_32.lib
    if %ERRORLEVEL% EQU 0 (
        echo C++ 版本编译成功
    ) else (
        echo C++ 版本编译失败
    )
) else (
    echo 未找到 MSVC 编译器
    echo 如需使用 Visual Studio 编译器，请在开发者命令提示符中运行此脚本
    echo.
)

echo.
echo 编译完成
pause