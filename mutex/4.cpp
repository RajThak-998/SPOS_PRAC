#include <iostream>
#include <pthread.h>
#include <semaphore.h>
#include <unistd.h>
using namespace std;

/* ===========================================================
   ===============  DINING PHILOSOPHERS CODE  ================
   =========================================================== */

#define N 5
sem_t chopstick[N];
pthread_mutex_t dp_mutex;

void *philosopher(void *num)
{
    int id = *(int *)num;

    for (int i = 0; i < 3; i++)
    {
        cout << "Philosopher " << id << " is thinking.\n";
        sleep(1);

        pthread_mutex_lock(&dp_mutex);
        sem_wait(&chopstick[id]);
        sem_wait(&chopstick[(id + 1) % N]);
        pthread_mutex_unlock(&dp_mutex);

        cout << "Philosopher " << id << " is eating.\n";
        sleep(1);

        sem_post(&chopstick[id]);
        sem_post(&chopstick[(id + 1) % N]);
        cout << "Philosopher " << id << " finished eating.\n";
    }
    return NULL;
}

void runDiningPhilosophers()
{
    pthread_t ph[5];
    int pid[5];

    for (int i = 0; i < N; i++)
        sem_init(&chopstick[i], 0, 1);

    pthread_mutex_init(&dp_mutex, NULL);

    for (int i = 0; i < N; i++)
    {
        pid[i] = i;
        pthread_create(&ph[i], NULL, philosopher, &pid[i]);
    }

    for (int i = 0; i < N; i++)
        pthread_join(ph[i], NULL);

    for (int i = 0; i < N; i++)
        sem_destroy(&chopstick[i]);
    pthread_mutex_destroy(&dp_mutex);

    cout << "\nAll philosophers finished.\n";
}

/* ===========================================================
   ===============  READER–WRITER PROBLEM CODE  ================
   =========================================================== */

sem_t wrt;
pthread_mutex_t rw_mutex;
int readcount = 0;
int shared_data = 0;

void *writer(void *arg)
{
    int id = *(int *)arg;

    for (int i = 0; i < 3; ++i)
    {
        sem_wait(&wrt);
        shared_data++;
        cout << "Writer " << id << " is writing. Shared = " << shared_data << endl;
        sleep(1);
        sem_post(&wrt);
        cout << "Writer " << id << " finished writing.\n";
        sleep(1);
    }
    return NULL;
}

void *reader(void *arg)
{
    int id = *(int *)arg;

    for (int i = 0; i < 3; ++i)
    {
        pthread_mutex_lock(&rw_mutex);
        readcount++;
        if (readcount == 1)
            sem_wait(&wrt);
        pthread_mutex_unlock(&rw_mutex);

        cout << "Reader " << id << " is reading. Shared = " << shared_data << endl;
        sleep(1);

        pthread_mutex_lock(&rw_mutex);
        readcount--;
        if (readcount == 0)
            sem_post(&wrt);
        pthread_mutex_unlock(&rw_mutex);

        sleep(1);
    }
    return NULL;
}

void runReaderWriter()
{
    pthread_t rthreads[5], wthreads[2];
    int rid[5], wid[2];

    sem_init(&wrt, 0, 1);
    pthread_mutex_init(&rw_mutex, NULL);

    for (int i = 0; i < 2; i++)
    {
        wid[i] = i + 1;
        pthread_create(&wthreads[i], NULL, writer, &wid[i]);
    }

    for (int i = 0; i < 5; i++)
    {
        rid[i] = i + 1;
        pthread_create(&rthreads[i], NULL, reader, &rid[i]);
    }

    for (int i = 0; i < 2; i++)
        pthread_join(wthreads[i], NULL);
    for (int i = 0; i < 5; i++)
        pthread_join(rthreads[i], NULL);

    sem_destroy(&wrt);
    pthread_mutex_destroy(&rw_mutex);

    cout << "\nAll readers & writers finished.\n";
}

/* ===========================================================
   ========================   MENU   ==========================
   =========================================================== */

int main()
{
    int choice;

    while (true)
    {
        cout << "\n========== MENU ==========\n";
        cout << "1. Dining Philosophers Problem\n";
        cout << "2. Reader-Writer Problem\n";
        cout << "3. Exit\n";
        cout << "Enter your choice: ";
        cin >> choice;

        switch (choice)
        {
            case 1:
                runDiningPhilosophers();
                break;
            case 2:
                runReaderWriter();
                break;
            case 3:
                return 0;
            default:
                cout << "Invalid choice! Try again.\n";
        }
    }

    return 0;
}
