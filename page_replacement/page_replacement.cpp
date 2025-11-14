#include <iostream>
#include <fstream>
#include <vector>
#include <queue>
#include <set>
#include <unordered_map>
#include <climits>
#include <limits> // for std::numeric_limits

struct Result {
    int hits;
    int faults;
};

void readPages(const std::string &filename, std::vector<int> &pages) {
    pages.clear();
    std::ifstream fin(filename);
    if (!fin) {
        std::cout << "Error opening file " << filename << std::endl;
        return;
    }
    int page;
    while (fin >> page) {
        pages.push_back(page);
    }
    fin.close();
}

Result fifo(const std::vector<int> &pages, int frames) {
    std::queue<int> q;
    std::set<int> s;
    int faults = 0;
    int hits = 0;

    for (int page : pages) {
        if (s.find(page) == s.end()) {
            faults++;
            if ((int)s.size() == frames) {
                int oldest = q.front();
                q.pop();
                s.erase(oldest);
            }
            s.insert(page);
            q.push(page);
        } else {
            hits++;
        }
    }
    return {hits, faults};
}

Result lru(const std::vector<int> &pages, int frames) {
    std::unordered_map<int, int> indexes; // page -> last used time
    std::set<int> s;                      // current pages in frame
    int faults = 0;
    int hits = 0;
    int time = 0;

    for (int page : pages) {
        if (s.find(page) == s.end()) {
            faults++;
            if ((int)s.size() == frames) {
                int lru_page = -1, min_time = INT_MAX;
                for (auto p : s) {
                    if (indexes[p] < min_time) {
                        min_time = indexes[p];
                        lru_page = p;
                    }
                }
                s.erase(lru_page);
                indexes.erase(lru_page);
            }
            s.insert(page);
        } else {
            hits++;
        }
        indexes[page] = time++;
    }

    return {hits, faults};
}

Result optimal(const std::vector<int> &pages, int frames) {
    std::set<int> s;
    int faults = 0;
    int hits = 0;

    for (int i = 0; i < (int)pages.size(); i++) {
        int page = pages[i];
        if (s.find(page) == s.end()) {
            faults++;
            if ((int)s.size() == frames) {
                int farthest = i + 1;
                int page_to_remove = -1;
                for (int p : s) {
                    int j;
                    for (j = i + 1; j < (int)pages.size(); j++) {
                        if (pages[j] == p) break;
                    }
                    if (j == (int)pages.size()) {
                        page_to_remove = p;
                        break;
                    }
                    if (j > farthest) {
                        farthest = j;
                        page_to_remove = p;
                    }
                }
                if (page_to_remove == -1) page_to_remove = *s.begin();
                s.erase(page_to_remove);
            }
            s.insert(page);
        } else {
            hits++;
        }
    }

    return {hits, faults};
}

int main() {
    int choice, frames;
    std::vector<int> pages;
    const std::string filename = "input.txt"; // unified input file

    while (true) {
        std::cout << "\nPage Replacement Algorithms Menu:\n";
        std::cout << "1. FIFO\n2. LRU\n3. Optimal\n4. Reload input file\n5. Exit\n";
        std::cout << "Enter your choice: ";
        if (!(std::cin >> choice)) {
            std::cout << "Invalid input. Exiting.\n";
            return 1;
        }
        if (choice == 5) break;

        if (choice == 4) {
            readPages(filename, pages);
            if (pages.empty()) {
                std::cout << "No pages read from file " << filename << "\n";
            } else {
                std::cout << "Loaded " << pages.size() << " pages from " << filename << "\n";
            }
            continue;
        }

        std::cout << "Enter number of frames: ";
        if (!(std::cin >> frames) || frames <= 0) {
            std::cout << "Invalid frame count. Try again.\n";
            // clear error state if needed
            if (std::cin.fail()) {
                std::cin.clear();
                std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
            }
            continue;
        }

        // Ensure pages loaded
        if (pages.empty()) {
            readPages(filename, pages);
            if (pages.empty()) {
                std::cout << "No pages read from file " << filename << ". Use option 4 to reload.\n";
                continue;
            }
        }

        Result res;
        switch (choice) {
            case 1:
                res = fifo(pages, frames);
                std::cout << "FIFO Page Hits = " << res.hits << "\n";
                std::cout << "FIFO Page Faults = " << res.faults << "\n";
                break;
            case 2:
                res = lru(pages, frames);
                std::cout << "LRU Page Hits = " << res.hits << "\n";
                std::cout << "LRU Page Faults = " << res.faults << "\n";
                break;
            case 3:
                res = optimal(pages, frames);
                std::cout << "Optimal Page Hits = " << res.hits << "\n";
                std::cout << "Optimal Page Faults = " << res.faults << "\n";
                break;
            default:
                std::cout << "Invalid choice.\n";
                break;
        }
    }

    std::cout << "Program terminated.\n";
    return 0;
}
