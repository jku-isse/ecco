class Circle extends Shape {

    radius:number

    constructor(radius : number) {
        super();
        this.radius = radius
    }

    getArea = () => {
        return this.radius * 2 * Math.PI
    }

    getName(){
        return "Circle"
    }
}