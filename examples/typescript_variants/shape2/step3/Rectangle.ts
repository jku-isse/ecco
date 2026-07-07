class Rectangle extends Shape {

    height:number
    width:number

    constructor(width:number,height:number) {
        super();
        this.width = width
        this.height = height
    }

    getArea = () => {
        return this.width * this.height
    }

    getName(){
        return "Rectangle"
    }
}