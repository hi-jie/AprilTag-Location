#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#ifdef _WIN32
    #include <winsock2.h>
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

typedef struct {
    double timestamp;
} TimePoint;

int main() {
#ifdef _WIN32
    WSADATA wsaData;
    WSAStartup(MAKEWORD(2, 2), &wsaData);
#endif

    int sockfd;
    struct sockaddr_in server_addr, client_addr;
    char buffer[BUFFER_SIZE];
    socklen_t client_len = sizeof(client_addr);
    
    // Create UDP socket
    sockfd = socket(AF_INET, SOCK_DGRAM, 0);
    if (sockfd < 0) {
        perror("socket creation failed");
        exit(EXIT_FAILURE);
    }
    
    // Configure server address
    memset(&server_addr, 0, sizeof(server_addr));
    server_addr.sin_family = AF_INET;
    server_addr.sin_addr.s_addr = INADDR_ANY;
    server_addr.sin_port = htons(PORT);
    
    // Bind socket
    if (bind(sockfd, (const struct sockaddr*)&server_addr, sizeof(server_addr)) < 0) {
        perror("bind failed");
        exit(EXIT_FAILURE);
    }
    
    printf("UDP receiver started, listening on port %d\n", PORT);
    printf("Waiting for data...\n");
    
    // Time tracking
    TimePoint *timestamps = malloc(BUFFER_SIZE * sizeof(TimePoint));
    int capacity = BUFFER_SIZE;
    int count = 0;
    int oldest_idx = 0;
    
    while (1) {
        // Receive data
        int n = recvfrom(sockfd, buffer, BUFFER_SIZE - 1, 0, 
                         (struct sockaddr*)&client_addr, &client_len);
        if (n < 0) {
            perror("recvfrom failed");
            break;
        }
        
        buffer[n] = '\0';  // Ensure string termination
        
        // Record current time
        double current_time = (double)clock() / CLOCKS_PER_SEC;
        timestamps[count % capacity].timestamp = current_time;
        count++;
        
        // Parse data (format: x,y,angle)
        float x, y, angle;
        if (sscanf(buffer, "%f,%f,%f", &x, &y, &angle) == 3) {
            printf("[%s:%d] X:%.3f, Y:%.3f, Angle:%.1f°\n", 
                   inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port), x, y, angle);
        } else {
            printf("[%s:%d] Unknown format: %s\n", 
                   inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port), buffer);
        }
        
        // Calculate and display frame rate (updated every second)
        if (count > 1) {
            int window_size = 30;
            if (count >= window_size) {
                int start_idx = (count - window_size + oldest_idx) % capacity;
                double time_diff = timestamps[(count - 1) % capacity].timestamp - 
                                  timestamps[start_idx].timestamp;
                
                if (time_diff > 0) {
                    double fps = (window_size - 1) / time_diff;
                    printf("Receive FPS: %.2f FPS\n", fps);
                }
            }
        }
    }
    
    free(timestamps);
    
#ifdef _WIN32
    closesocket(sockfd);
    WSACleanup();
#else
    close(sockfd);
#endif

    return 0;
}