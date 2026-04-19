import java.util.PriorityQueue

data class Event(val time: Long, val action: () -> Unit) : Comparable<Event> {
    override fun compareTo(other: Event) = time.compareTo(other.time)
}

val queue = PriorityQueue<Event>()
var timeNow: Long = 0
var springCount: Long = 0

fun postDelayed(delay: Long, action: () -> Unit) {
    queue.add(Event(timeNow + delay, action))
}

fun startFloatingAnimation() {
    for (index in 0..5) {
        postDelayed(index * 500L) {
            springCount += 2
            postDelayed(6000L + index * 1000L) {
                reverseFloatingAnimation()
            }
        }
    }
}

fun reverseFloatingAnimation() {
    springCount += 2
    postDelayed(6000L) {
        startFloatingAnimation()
    }
}

fun main() {
    startFloatingAnimation()
    var loops = 0
    while (queue.isNotEmpty() && timeNow <= 60000L) { // Simulate 60 seconds
        val event = queue.poll()
        timeNow = event.time
        event.action()
        loops++
    }
    println("Time: ${timeNow}ms, total events: $loops, spring calculations: $springCount")
}
