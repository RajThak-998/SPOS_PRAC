#include <iostream>
#include <vector>
#include <iomanip>
using namespace std;

struct Block
{
    int size;
    bool allocated;
};

// Function to display allocation results
void displayResult(vector<Block> blocks, vector<int> process, vector<int> allocation)
{
    int total_fragmentation = 0;
    cout << "\nProcess\tProcess Size\tBlock Allocated\tFragmentation\n";
    cout << "-----------------------------------------------------------\n";

    for (int i = 0; i < process.size(); i++)
    {
        if (allocation[i] != -1)
        {
            int frag = blocks[allocation[i]].size - process[i];
            total_fragmentation += frag;
            cout << "P" << i + 1 << "\t" << process[i] << "\t\tB" << allocation[i] + 1
                 << "\t\t" << frag << "\n";
        }
        else
        {
            cout << "P" << i + 1 << "\t" << process[i] << "\t\tNot Allocated\t---\n";
        }
    }

    int remaining = 0;
    for (auto &b : blocks)
        if (!b.allocated)
            remaining += b.size;

    cout << "-----------------------------------------------------------\n";
    cout << "Total Fragmentation: " << total_fragmentation << endl;
    cout << "Remaining Free Memory: " << remaining << endl;
    cout << "-----------------------------------------------------------\n";
}

// Allocation Strategies
void firstFit(vector<Block> blocks, vector<int> process)
{
    cout << "\n====== FIRST FIT ======\n";
    vector<int> allocation(process.size(), -1);
    for (int i = 0; i < process.size(); i++)
    {
        for (int j = 0; j < blocks.size(); j++)
        {
            if (!blocks[j].allocated && blocks[j].size >= process[i])
            {
                allocation[i] = j;
                blocks[j].allocated = true;
                break;
            }
        }
    }
    displayResult(blocks, process, allocation);
}

void bestFit(vector<Block> blocks, vector<int> process)
{
    cout << "\n====== BEST FIT ======\n";
    vector<int> allocation(process.size(), -1);
    for (int i = 0; i < process.size(); i++)
    {
        int bestIdx = -1;
        for (int j = 0; j < blocks.size(); j++)
        {
            if (!blocks[j].allocated && blocks[j].size >= process[i])
            {
                if (bestIdx == -1 || blocks[j].size < blocks[bestIdx].size)
                    bestIdx = j;
            }
        }
        if (bestIdx != -1)
            blocks[bestIdx].allocated = true, allocation[i] = bestIdx;
    }
    displayResult(blocks, process, allocation);
}

void worstFit(vector<Block> blocks, vector<int> process)
{
    cout << "\n====== WORST FIT ======\n";
    vector<int> allocation(process.size(), -1);
    for (int i = 0; i < process.size(); i++)
    {
        int worstIdx = -1;
        for (int j = 0; j < blocks.size(); j++)
        {
            if (!blocks[j].allocated && blocks[j].size >= process[i])
            {
                if (worstIdx == -1 || blocks[j].size > blocks[worstIdx].size)
                    worstIdx = j;
            }
        }
        if (worstIdx != -1)
            blocks[worstIdx].allocated = true, allocation[i] = worstIdx;
    }
    displayResult(blocks, process, allocation);
}

void nextFit(vector<Block> blocks, vector<int> process)
{
    cout << "\n====== NEXT FIT ======\n";
    vector<int> allocation(process.size(), -1);
    int pos = 0;
    for (int i = 0; i < process.size(); i++)
    {
        int j;
        for (j = 0; j < blocks.size(); j++)
        {
            int idx = (pos + j) % blocks.size();
            if (!blocks[idx].allocated && blocks[idx].size >= process[i])
            {
                allocation[i] = idx;
                blocks[idx].allocated = true;
                pos = idx;
                break;
            }
        }
    }
    displayResult(blocks, process, allocation);
}

int main()
{
    int n, m, choice;

    cout << "Enter number of memory blocks: ";
    cin >> n;
    vector<Block> blocks(n);
    cout << "Enter sizes of memory blocks:\n";
    for (int i = 0; i < n; i++)
    {
        cin >> blocks[i].size;
        blocks[i].allocated = false;
    }

    cout << "Enter number of processes: ";
    cin >> m;
    vector<int> process(m);
    cout << "Enter sizes of processes:\n";
    for (int i = 0; i < m; i++)
        cin >> process[i];

    do
    {
        cout << "\n==============================\n";
        cout << "  MEMORY ALLOCATION MENU\n";
        cout << "==============================\n";
        cout << "1. First Fit\n";
        cout << "2. Best Fit\n";
        cout << "3. Worst Fit\n";
        cout << "4. Next Fit\n";
        cout << "5. Exit\n";
        cout << "Enter your choice: ";
        cin >> choice;

        switch (choice)
        {
        case 1:
            firstFit(blocks, process);
            break;
        case 2:
            bestFit(blocks, process);
            break;
        case 3:
            worstFit(blocks, process);
            break;
        case 4:
            nextFit(blocks, process);
            break;
        case 5:
            cout << "Exiting program...\n";
            break;
        default:
            cout << "Invalid choice! Try again.\n";
        }
    } while (choice != 5);

    return 0;
}
