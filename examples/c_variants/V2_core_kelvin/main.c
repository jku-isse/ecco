#include <stdio.h>

float celsiusToFahrenheit(float celsius) {
    return celsius * 9.0 / 5.0 + 32.0;
}

float celsiusToKelvin(float celsius) {
    return celsius + 273.15;
}

int main() {
    float celsius = 25.0;

    float fahrenheit = celsiusToFahrenheit(celsius);
    printf("%.1fC = %.1fF\n", celsius, fahrenheit);

    float kelvin = celsiusToKelvin(celsius);
    printf("%.1fC = %.2fK\n", celsius, kelvin);

    return 0;
}
