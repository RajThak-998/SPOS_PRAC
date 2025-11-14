#include <bits/stdc++.h>
using namespace std;

struct Process
{
    int pid = 0, at = 0, bt = 0, pr = 0;
    int ct = 0, tat = 0, wt = 0, rt = 0, start = 0;
};

/* ----------- GANTT CHART PRINTER ----------- */
void printGantt(const vector<pair<int, int>> &gantt, int end_time)
{
    if (gantt.empty())
    {
        cout << "No execution segments to display\n";
        return;
    }

    // Process sequence
    for (size_t i = 0; i < gantt.size(); ++i)
    {
        cout << "P" << gantt[i].first;
        if (i + 1 < gantt.size())
            cout << " | ";
    }
    cout << "\n";

    // Time labels
    cout << gantt[0].second;
    for (size_t i = 1; i < gantt.size(); ++i)
        cout << setw(6) << gantt[i].second;

    cout << setw(6) << end_time << "\n";
}

/* -------------------- FCFS -------------------- */
void FCFS()
{
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt;
    }

    sort(p.begin(), p.end(), [](auto &a, auto &b)
         { return a.at < b.at; });

    int time = 0;
    double totalWT = 0, totalTAT = 0;
    vector<pair<int, int>> gantt;

    for (auto &pr : p)
    {
        if (time < pr.at)
            time = pr.at;

        gantt.push_back({pr.pid, time});
        time += pr.bt;
        pr.ct = time;
        pr.tat = pr.ct - pr.at;
        pr.wt = pr.tat - pr.bt;

        totalWT += pr.wt;
        totalTAT += pr.tat;
    }

    cout << "\nPID AT BT CT TAT WT\n";
    for (auto &pr : p)
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt << " "
             << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- SJF NON-PREEMPTIVE -------------------- */
void SJF_NonPreemptive()
{
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt;
    }

    int time = 0, completed = 0;
    vector<bool> done(n, false);
    double totalWT = 0, totalTAT = 0;
    vector<pair<int, int>> gantt;

    while (completed < n)
    {
        int idx = -1, mn = INT_MAX;

        for (int i = 0; i < n; i++)
            if (!done[i] && p[i].at <= time && p[i].bt < mn)
            {
                idx = i;
                mn = p[i].bt;
            }

        if (idx == -1)
        {
            time++;
            continue;
        }

        gantt.push_back({p[idx].pid, time});

        time += p[idx].bt;
        p[idx].ct = time;
        p[idx].tat = p[idx].ct - p[idx].at;
        p[idx].wt = p[idx].tat - p[idx].bt;

        done[idx] = true;
        completed++;

        totalWT += p[idx].wt;
        totalTAT += p[idx].tat;
    }

    cout << "\nPID AT BT CT TAT WT\n";
    for (auto &pr : p)
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt
             << " " << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- SJF PREEMPTIVE -------------------- */
void SJF_Preemptive()
{
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt;
        p[i].rt = p[i].bt;
    }

    int time = 0, completed = 0, prev = -1;
    double totalWT = 0, totalTAT = 0;
    vector<pair<int, int>> gantt;

    while (completed < n)
    {
        int idx = -1, mn = INT_MAX;
        for (int i = 0; i < n; i++)
            if (p[i].at <= time && p[i].rt > 0 && p[i].rt < mn)
            {
                idx = i;
                mn = p[i].rt;
            }

        if (idx == -1)
        {
            time++;
            continue;
        }

        if (prev != p[idx].pid)
        {
            gantt.push_back({p[idx].pid, time});
            prev = p[idx].pid;
        }

        p[idx].rt--;
        time++;

        if (p[idx].rt == 0)
        {
            completed++;
            p[idx].ct = time;
            p[idx].tat = p[idx].ct - p[idx].at;
            p[idx].wt = p[idx].tat - p[idx].bt;

            totalWT += p[idx].wt;
            totalTAT += p[idx].tat;
        }
    }

    cout << "\nPID AT BT CT TAT WT\n";
    for (auto &pr : p)
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt
             << " " << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- PRIORITY NON-PREEMPTIVE -------------------- */
void Priority_NonPreemptive()
{
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT PRIORITY for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt >> p[i].pr;
    }

    int time = 0, completed = 0;
    vector<bool> done(n, false);
    double totalWT = 0, totalTAT = 0;
    vector<pair<int, int>> gantt;

    while (completed < n)
    {
        int idx = -1, best = INT_MAX;

        for (int i = 0; i < n; i++)
            if (!done[i] && p[i].at <= time && p[i].pr < best)
            {
                best = p[i].pr;
                idx = i;
            }

        if (idx == -1)
        {
            time++;
            continue;
        }

        gantt.push_back({p[idx].pid, time});

        time += p[idx].bt;
        p[idx].ct = time;
        p[idx].tat = p[idx].ct - p[idx].at;
        p[idx].wt = p[idx].tat - p[idx].bt;

        done[idx] = true;
        completed++;

        totalWT += p[idx].wt;
        totalTAT += p[idx].tat;
    }

    cout << "\nPID AT BT PR CT TAT WT\n";
    for (auto &pr : p)
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt
             << " " << pr.pr << " " << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- PRIORITY PREEMPTIVE -------------------- */
void Priority_Preemptive()
{
    int n;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT PRIORITY for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt >> p[i].pr;
        p[i].rt = p[i].bt;
    }

    int time = 0, completed = 0, prev = -1;
    double totalWT = 0, totalTAT = 0;
    vector<pair<int, int>> gantt;

    while (completed < n)
    {
        int idx = -1, best = INT_MAX;
        // select the process with arrived, remaining time > 0 and smallest priority value
        for (int i = 0; i < n; i++)
        {
            if (p[i].at <= time && p[i].rt > 0)
            {
                if (p[i].pr < best || (p[i].pr == best && p[i].at < p[idx].at))
                {
                    best = p[i].pr;
                    idx = i;
                }
            }
        }

        if (idx == -1)
        {
            time++;
            continue;
        }

        // record a new gantt segment when context switches to a different process
        if (prev != p[idx].pid)
        {
            gantt.push_back({p[idx].pid, time});
            prev = p[idx].pid;
        }

        // execute for 1 time unit (preemptive)
        p[idx].rt--;
        time++;

        // if process finished
        if (p[idx].rt == 0)
        {
            completed++;
            p[idx].ct = time;
            p[idx].tat = p[idx].ct - p[idx].at;
            p[idx].wt = p[idx].tat - p[idx].bt;

            totalWT += p[idx].wt;
            totalTAT += p[idx].tat;
        }
    }

    cout << "\nPID AT BT PR CT TAT WT\n";
    for (auto &pr : p)
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt
             << " " << pr.pr << " " << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- ROUND ROBIN -------------------- */
void RoundRobin()
{
    int n, tq;
    cout << "Enter number of processes: ";
    cin >> n;

    vector<Process> p(n);
    for (int i = 0; i < n; i++)
    {
        p[i].pid = i + 1;
        cout << "Enter AT BT for P" << i + 1 << ": ";
        cin >> p[i].at >> p[i].bt;
        p[i].rt = p[i].bt;
    }

    cout << "Enter Time Quantum: ";
    cin >> tq;

    queue<int> q;
    vector<bool> inQ(n, false);
    vector<pair<int, int>> gantt;

    int time = 0, completed = 0;

    while (completed < n)
    {
        for (int i = 0; i < n; i++)
            if (!inQ[i] && p[i].at <= time && p[i].rt > 0)
            {
                q.push(i);
                inQ[i] = true;
            }

        if (q.empty())
        {
            time++;
            continue;
        }

        int idx = q.front();
        q.pop();
        gantt.push_back({p[idx].pid, time});

        int exec = min(tq, p[idx].rt);
        p[idx].rt -= exec;
        time += exec;

        for (int i = 0; i < n; i++)
            if (!inQ[i] && p[i].at <= time && p[i].rt > 0)
            {
                q.push(i);
                inQ[i] = true;
            }

        if (p[idx].rt > 0)
            q.push(idx);
        else
        {
            p[idx].ct = time;
            p[idx].tat = p[idx].ct - p[idx].at;
            p[idx].wt = p[idx].tat - p[idx].bt;
            completed++;
        }
    }

    cout << "\nPID AT BT CT TAT WT\n";
    double totalWT = 0, totalTAT = 0;

    for (auto &pr : p)
    {
        cout << "P" << pr.pid << " " << pr.at << " " << pr.bt
             << " " << pr.ct << " " << pr.tat << " " << pr.wt << "\n";

        totalWT += pr.wt;
        totalTAT += pr.tat;
    }

    cout << "Average WT=" << totalWT / n << "\n";
    cout << "Average TAT=" << totalTAT / n << "\n";

    cout << "\nGantt Chart:\n";
    printGantt(gantt, time);
}

/* -------------------- MAIN MENU -------------------- */
int main()
{
    while (true)
    {
        cout << "\n====== CPU SCHEDULING MENU ======\n";
        cout << "1. FCFS\n";
        cout << "2. SJF Non-Preemptive\n";
        cout << "3. SJF Preemptive\n";
        cout << "4. Priority Non-preemptive\n";
        cout << "5. Round Robin\n";
        cout << "6. Priority Preemptive\n";
        cout << "7. Exit\n";
        cout << "Enter choice: ";

        int ch;
        cin >> ch;

        switch (ch)
        {
        case 1:
            FCFS();
            break;
        case 2:
            SJF_NonPreemptive();
            break;
        case 3:
            SJF_Preemptive();
            break;
        case 4:
            Priority_NonPreemptive();
            break;
        case 5:
            RoundRobin();
            break;
        case 6:
            Priority_Preemptive();
            break;
        case 7:
            return 0;
        default:
            cout << "Invalid choice\n";
        }
    }
}
