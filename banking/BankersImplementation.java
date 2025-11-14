import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class BankersImplementation {
    private int available[];
    private int maximum[][], allocation[][], need[][];
    private boolean isCompleted[];
    private int sequence[];
    private int noOfresources, noOfprocesses;
    private static Scanner scanner;

    // Constructor
    BankersImplementation(int noOfprocesses, int noOfresources, int maximum[][], int allocation[][], int available[]) {
        sequence = new int[noOfprocesses];
        isCompleted = new boolean[noOfprocesses];
        need = new int[noOfprocesses][noOfresources];
        this.maximum = maximum;
        this.allocation = allocation;
        this.noOfprocesses = noOfprocesses;
        this.noOfresources = noOfresources;
        this.available = available;
    }

    // Calculate Need Matrix
    public void calculateNeedMatrix() {
        for (int i = 0; i < noOfprocesses; i++) {
            for (int j = 0; j < noOfresources; j++) {
                need[i][j] = maximum[i][j] - allocation[i][j];
            }
        }
    }

    // Display Need Matrix
    public void displayNeedMatrix() {
        System.out.println("\nNeed Matrix:");
        System.out.print("     ");
        for (int j = 0; j < noOfresources; j++) {
            System.out.print("R" + j + " ");
        }
        System.out.println();
        for (int i = 0; i < noOfprocesses; i++) {
            System.out.print("P" + i + " : ");
            for (int j = 0; j < noOfresources; j++) {
                System.out.print(need[i][j] + "  ");
            }
            System.out.println();
        }
    }

    // Calculate and display safe sequence
    public void calculateSafeSequence() {
        int count = 0;

        while (count < noOfprocesses) {
            boolean flag = false;
            for (int i = 0; i < noOfprocesses; i++) {
                boolean execute = true;
                if (!isCompleted[i]) {
                    for (int j = 0; j < noOfresources; j++) {
                        if (need[i][j] > available[j]) {
                            execute = false;
                            break;
                        }
                    }
                    if (execute) {
                        for (int j = 0; j < noOfresources; j++) {
                            available[j] += allocation[i][j];
                        }
                        sequence[count++] = i;
                        isCompleted[i] = true;
                        flag = true;
                    }
                }
            }
            if (!flag) break; // No process could be executed in this pass => unsafe
        }

        if (count < noOfprocesses) {
            System.out.println("\nSYSTEM IS UNSAFE. No safe sequence exists.");
        } else {
            System.out.println("\nSYSTEM IS SAFE.");
            System.out.print("SAFE SEQUENCE: ");
            for (int i = 0; i < sequence.length; i++) {
                System.out.print("P" + sequence[i]);
                if (i != sequence.length - 1)
                    System.out.print(" -> ");
            }
            System.out.println();
        }
    }

    // Main method
    public static void main(String[] args) {
        try {
            scanner = new Scanner(new File("input.txt")); // Read from file
        } catch (FileNotFoundException e) {
            System.out.println("input.txt not found. Please ensure the file exists.");
            return;
        }

        System.out.println("=== Banker's Algorithm Implementation ===");

        int noOfprocesses, noOfresources;
        int maximum[][], allocation[][], available[];

        // Reading input
        noOfprocesses = scanner.nextInt();
        noOfresources = scanner.nextInt();

        available = new int[noOfresources];
        allocation = new int[noOfprocesses][noOfresources];
        maximum = new int[noOfprocesses][noOfresources];

        // Available resources
        for (int i = 0; i < noOfresources; i++) {
            available[i] = scanner.nextInt();
        }

        // Allocation matrix
        for (int i = 0; i < noOfprocesses; i++) {
            for (int j = 0; j < noOfresources; j++) {
                allocation[i][j] = scanner.nextInt();
            }
        }

        // Maximum matrix
        for (int i = 0; i < noOfprocesses; i++) {
            for (int j = 0; j < noOfresources; j++) {
                maximum[i][j] = scanner.nextInt();
            }
        }

        BankersImplementation bankers = new BankersImplementation(noOfprocesses, noOfresources, maximum, allocation, available);
        bankers.calculateNeedMatrix();
        bankers.displayNeedMatrix();
        bankers.calculateSafeSequence();
    }
}
