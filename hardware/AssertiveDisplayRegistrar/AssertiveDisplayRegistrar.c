#include <errno.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>

#define PPS_SOCKET_PATH "/dev/socket/pps"
#define MAX_ATTEMPTS 10
#define RETRY_DELAY_SECONDS 1

static const char kCommand[] = "ad:on;1";

static int write_all(int fd, const void *buffer, size_t size) {
    const char *data = buffer;
    size_t written = 0;

    while (written < size) {
        ssize_t result =
                send(fd, data + written, size - written, MSG_NOSIGNAL);

        if (result > 0) {
            written += (size_t) result;
            continue;
        }

        if (result < 0 && errno == EINTR) {
            continue;
        }

        return -1;
    }

    return 0;
}

static int send_enable_command(void) {
    int fd = socket(AF_UNIX, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) {
        fprintf(stderr, "assertive-display: socket failed: %s\n",
                strerror(errno));
        return -1;
    }

    struct sockaddr_un address;
    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;

    if (strlen(PPS_SOCKET_PATH) >= sizeof(address.sun_path)) {
        fprintf(stderr, "assertive-display: socket path is too long\n");
        close(fd);
        return -1;
    }

    strcpy(address.sun_path, PPS_SOCKET_PATH);

    socklen_t address_length =
            (socklen_t) (offsetof(struct sockaddr_un, sun_path)
                    + strlen(address.sun_path) + 1);

    if (connect(fd, (struct sockaddr *) &address, address_length) < 0) {
        fprintf(stderr, "assertive-display: connect failed: %s\n",
                strerror(errno));
        close(fd);
        return -1;
    }

    if (write_all(fd, kCommand, sizeof(kCommand) - 1) < 0) {
        fprintf(stderr, "assertive-display: send failed: %s\n",
                strerror(errno));
        close(fd);
        return -1;
    }

    /*
     * Tell the server that no more command bytes are coming.
     * The command itself has already been copied into the socket buffer.
     */
    shutdown(fd, SHUT_WR);
    close(fd);

    fprintf(stderr, "assertive-display: sent \"%s\"\n", kCommand);
    return 0;
}

int main(void) {
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; ++attempt) {
        if (send_enable_command() == 0) {
            return 0;
        }

        if (attempt < MAX_ATTEMPTS) {
            fprintf(stderr,
                    "assertive-display: attempt %d/%d failed; retrying\n",
                    attempt, MAX_ATTEMPTS);
            sleep(RETRY_DELAY_SECONDS);
        }
    }

    fprintf(stderr,
            "assertive-display: failed after %d attempts\n",
            MAX_ATTEMPTS);
    return 1;
}
