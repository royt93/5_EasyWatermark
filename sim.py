def calculate_ram(rounds):
    # Total Runnables across N rounds
    total_runnables = 0
    current_layer = sum([1 for _ in range(6)])  # Index 0 to 5 -> 6 triggers
    for i in range(1, rounds + 1):
        total_runnables += current_layer
        current_layer *= 6
    return total_runnables

print(f"Rounds 1 to 5: {calculate_ram(5)} messages")
print(f"Rounds 1 to 10: {calculate_ram(10)} messages")
