#include <stdio.h>
#include "converter.h"

int main() {
    float celsius = 25.0;

    float fahrenheit = celsiusToFahrenheit(celsius);
    printf("%.1fC = %.1fF\n", celsius, fahrenheit);

    return 0;
}
