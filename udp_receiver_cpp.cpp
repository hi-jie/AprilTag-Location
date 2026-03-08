#include <iostream>
#include <string>
#include <vector>
#include <sstream>
#include <chrono>
#include <cmath>
#ifdef _WIN32
    #include <winsock2.h>
    #include <ws2tcpip.h>
    #pragma comment(lib, "ws2_32.lib")
    typedef int socklen_t;
#else
    #include <sys/socket.h>
    #include <netinet/in.h>
    #include <arpa/inet.h>
    #include <unistd.h>
#endif

#define PORT 8080
#define BUFFER_SIZE 1024

int main() {
#ifdef _WIN32
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) {
        std::cerr << "WSAStartup failed" << std::endl;
        return 1;
    }
#endif

    int sockfd;
    struct sockaddr_in server_addr, client_addr;
    char buffer[BUFFER_SIZE];
    socklen_t client_len = sizeof(client_addr);
    
    // Create UDP socket
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        std::cerr << "Socket creation failed" << std::endl;
#ifdef _WIN32
        WSACleanup();
#endif
        return 1;
    }
    
    // Allow port reuse
    int opt = 1;
    if (setsockopt(sockfd, SOL_SOCKET, SO_REUSEADDR, (const char*)&opt, sizeof(opt)) < 0) {
        std::cerr << "setsockopt SO_REUSEADDR failed" << std::endl;
    }
    
    // Configure server address
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;  // Listen on all interfaces
    server_addr.sin_port = htons(PORT);
    
    // Bind socket
    if (bind(sockfd, (const struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        std::cerr << "Bind failed. Error: ";
#ifdef _WIN32
        std::cerr << WSAGetLastError();
#else
        std::cerr << strerror(errno);
#endif
        std::cerr << std::endl;
#ifdef _WIN32
        closesocket(sockfd);
        WSACleanup();
#else
        close(sockfd);
#endif
        return 1;
    }
    
    std::cout << "UDP receiver started, listening on port " << PORT << std::endl;
    std::cout << "Make sure Android app is configured to send to this computer's IP address on port " << PORT << std::endl;
    std::cout << "Waiting for data..." << std::endl;
    
    // Time tracking
    std::vector<double> timestamps;
    const int WINDOW_SIZE = 30;
    
    while (true) {
        // Receive data
        int n = recvfrom(sockfd, buffer, BUFFER_SIZE - 1, 0, 
                         (struct sockaddr*)&client_addr, &client_len);
        if (n < 0) {
            std::cerr << "Recvfrom failed. Error: ";
#ifdef _WIN32
            std::cerr << WSAGetLastError();
#else
            std::cerr << strerror(errno);
#endif
            std::cerr << std::endl;
            break;
        }
        
        buffer[n] = '\0';  // Ensure string termination
        
        // Record current time
        auto now = std::chrono::high_resolution_clock::now();
        double current_time = std::chrono::duration<double>(now.time_since_epoch()).count();
        timestamps.push_back(current_time);
        
        // Get sender IP and port
        std::string sender_ip = inet_ntoa(client_addr.sin_addr);
        int sender_port = ntohs(client_addr.sin_port);
        
        // Output raw received data for debugging
        std::cout << "[From: " << sender_ip << ":" << sender_port << "] Raw data: '" << buffer << "'" << std::endl;
        
        // Parse data (format: x,y,angle)
        std::string data(buffer);
        std::istringstream iss(data);
        std::string token;
        std::vector<float> values;
        
        while (std::getline(iss, token, ',')) {
            try {
                values.push_back(std::stof(token));
            } catch (...) {
                break;
            }
        }
        
        if (values.size() == 3) {
            float x = values[0];
            float y = values[1];
            float angle = values[2];
            std::cout << "[" << sender_ip << ":" << sender_port << "] "
                      << "X:" << x << ", Y:" << y << ", Angle:" << angle << "°" << std::endl;
        } else {
            std::cout << "[" << sender_ip << ":" << sender_port << "] "
                      << "Unknown format: " << data << std::endl;
        }
        
        // Calculate and display frame rate (keep only recent timestamps)
        if (timestamps.size() > 1) {
            if (timestamps.size() > WINDOW_SIZE) {
                timestamps.erase(timestamps.begin());  // Remove oldest timestamp
            }
            
            if (timestamps.size() >= 2) {
                double time_diff = timestamps.back() - timestamps.front();
                if (time_diff > 0) {
                    double fps = (timestamps.size() - 1) / time_diff;
                    std::cout << "Receive FPS: " << fps << " FPS" << std::endl;
                }
            }
        }
    }
    
#ifdef _WIN32
    closesocket(sockfd);
    WSACleanup();
#else
    close(sockfd);
#endif

    return 0;
}