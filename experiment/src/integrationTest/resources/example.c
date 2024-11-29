#include <stdio.h>
#include "media_operations.h"

// Audio support
#ifdef AUDIO
void play_audio(const char* audio_file) {
    printf("Playing audio: %s\n", audio_file);
}
#endif

// Video support
#ifdef VIDEO
void play_video(const char* video_file) {
    printf("Playing video: %s\n", video_file);
}

    // Subtitle support
    #ifdef SUBTITLES
    void display_subtitles(const char* subtitle_file) {
        printf("Displaying subtitles from: %s\n", subtitle_file);
    }
    #endif

#endif

