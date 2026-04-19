fun animateRandomly(circle: View) {
    val currentX = circle.translationX
    val currentY = circle.translationY
    val targetX = currentX + ((-100..100).random()).toFloat()
    val targetY = currentY + ((-100..100).random()).toFloat()
    
    // ... animate to target, then recursively call animateRandomly(circle)
}
