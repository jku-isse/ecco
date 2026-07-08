#include <stdio.h>
#include "converter.h"

int main() {
    float celsius = 25.0;

    if (!isValidTemperature(celsius)) {
        printf("Invalid temperature: %.1fC\n", celsius);
        return 1;
    }

    logConversion(celsius);

    float fahrenheit = celsiusToFahrenheit(celsius);
    printf("%.1fC = %.1fF\n", celsius, fahrenheit);

    float kelvin = celsiusToKelvin(celsius);
    printf("%.1fC = %.2fK\n", celsius, kelvin);

    return 0;
}
