package main

import "fmt"

func main() {
	if true {
		printHelloWorld()
	} else {
		fmt.Println("No Hello World")
	}
}

func printHelloWorld() {
	fmt.Println("Hello, 世界")
}
