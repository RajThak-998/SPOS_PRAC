#include <iostream>
#include <thread>
#include <mutex>
#include <condition_variable>
#include <chrono>
#include <vector>
#include <cstdlib>
#include <ctime>

using namespace std;

// ====== Simple Semaphore Implementation ======
class SimpleSemaphore {
    mutex mtx;
    condition_variable cv;
    int count;

public:
    explicit SimpleSemaphore(int count_ = 0) : count(count_) {}

    void acquire() {
        unique_lock<mutex> lock(mtx);
        cv.wait(lock, [&]() { return count > 0; });
        --count;
    }

    void release() {
        {
            unique_lock<mutex> lock(mtx);
            ++count;
        }
        cv.notify_one();
    }
};

// ====== Dining Philosophers Problem ======

const int N_PHIL = 5;
mutex forks[N_PHIL];
SimpleSemaphore room(N_PHIL - 1);

void philosopher(int id, int rounds) {
    for (int r = 0; r < rounds; ++r) {
        cout << "[Philosopher " << id << "] Thinking...\n";
        this_thread::sleep_for(chrono::milliseconds(500 + rand() % 500));

        room.acquire();
        forks[id].lock();
        forks[(id + 1) % N_PHIL].lock();

        cout << "[Philosopher " << id << "] Eating...\n";
        this_thread::sleep_for(chrono::milliseconds(500 + rand() % 500));

        forks[id].unlock();
        forks[(id + 1) % N_PHIL].unlock();
        room.release();
    }

    cout << "[Philosopher " << id << "] Done dining.\n";
}

void runDiningPhilosophers() {
    cout << "\n=== Dining Philosophers Problem ===\n";
    vector<thread> philosophers;
    int rounds = 3; // each philosopher eats 3 times
    for (int i = 0; i < N_PHIL; i++)
        philosophers.emplace_back(philosopher, i, rounds);

    for (auto &t : philosophers)
        t.join();

    cout << "All philosophers finished dining.\n";
}

// ====== Readers-Writers Problem ======

int readCount = 0;
mutex readCountMutex;
SimpleSemaphore resource(1);
SimpleSemaphore serviceQueue(1);

void reader(int id, int rounds) {
    for (int r = 0; r < rounds; ++r) {
        serviceQueue.acquire();
        {
            lock_guard<mutex> lock(readCountMutex);
            if (++readCount == 1)
                resource.acquire();
        }
        serviceQueue.release();

        cout << "[Reader " << id << "] Reading data...\n";
        this_thread::sleep_for(chrono::milliseconds(400 + rand() % 400));

        {
            lock_guard<mutex> lock(readCountMutex);
            if (--readCount == 0)
                resource.release();
        }

        this_thread::sleep_for(chrono::milliseconds(400 + rand() % 600));
    }

    cout << "[Reader " << id << "] Finished reading.\n";
}

void writer(int id, int rounds) {
    for (int r = 0; r < rounds; ++r) {
        serviceQueue.acquire();
        resource.acquire();
        serviceQueue.release();

        cout << "[Writer " << id << "] Writing data...\n";
        this_thread::sleep_for(chrono::milliseconds(600 + rand() % 600));

        resource.release();
        this_thread::sleep_for(chrono::milliseconds(600 + rand() % 800));
    }

    cout << "[Writer " << id << "] Finished writing.\n";
}

void runReadersWriters() {
    cout << "\n=== Readers-Writers Problem ===\n";
    vector<thread> readers, writers;
    int rounds = 3; // each reader/writer does 3 rounds

    for (int i = 0; i < 5; i++)
        readers.emplace_back(reader, i, rounds);
    for (int i = 0; i < 2; i++)
        writers.emplace_back(writer, i, rounds);

    for (auto &t : readers)
        t.join();
    for (auto &t : writers)
        t.join();

    cout << "All readers and writers finished.\n";
}

// ====== Main Menu ======

int main() {
    srand(static_cast<unsigned int>(time(nullptr)));

    int choice;
    cout << "==============================\n";
    cout << "   Synchronization Problems\n";
    cout << "==============================\n";

    while (true) {
        cout << "\nChoose an option:\n";
        cout << "1. Dining Philosophers Problem\n";
        cout << "2. Readers-Writers Problem\n";
        cout << "3. Exit\n";
        cout << "Enter your choice: ";
        cin >> choice;

        switch (choice) {
            case 1:
                runDiningPhilosophers();
                break;
            case 2:
                runReadersWriters();
                break;
            case 3:
                cout << "Exiting...\n";
                return 0;
            default:
                cout << "Invalid choice. Try again.\n";
        }
    }
}
