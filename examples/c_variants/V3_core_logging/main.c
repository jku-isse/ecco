#include <stdio.h>

float celsiusToFahrenheit(float celsius) {
    return celsius * 9.0 / 5.0 + 32.0;
}

void logConversion(float celsius) {
    printf("[LOG] converting %.1fC\n", celsius);
}

int main() {
    float celsius = 25.0;

    logConversion(celsius);

    float fahrenheit = celsiusToFahrenheit(celsius);
    printf("%.1fC = %.1fF\n", celsius, fahrenheit);

    return 0;
}
