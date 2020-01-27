## git commands

```bash
git clone <repo link>
# download a remote repository and link it
# e.g. (for ssh) git@github.com:battlecode/lectureplayer.git

git status
# current status of git and your code

git pull
# pulls remote (github) commits to local (your computer)

git push 
# pushes local (your computer) commits to remote (github)

git diff
# show all the changes since the last commit
git diff <commitA> <commitB>
# show the changes which occured from commitA to commitB

git add
# often used with -A
# select which changes you're ready to commit

git commit
# often used with -m "commit message" to add a message in-line
# make a snapshot of the code. a 'save' in the tracked history
# commits are what you upload and download to collaborate

git branch
# lists local branches
# (may be more on remote, use git checkout <name> to see them)

git checkout <branch name or commit>
# switch to different branch
# add -b to create a new branch

git reset <commit>
# undo commits after <commit>
# adding --hard will throw away all changes, otherwise they're just unstaged

git merge <branch>
# makes a new commit merging all of the changes on <branch> into the curent branch

git rebase <branch/commit>
# usually used with -i for interactive mode
# takes all commits since the given branch/commit and appends them to the end
# creates all new commits
# never do this on a branch anyone else has downloaded: it rewrites history

rm -rf <repo name> && git clone <repo link>
# 'nuking' your local repo and getting a fresh copy.
# sometimes the easiest option.
# be careful out there
```

You can use `HEAD~N` to refer to 'N commits back'.
E.g. `git diff HEAD~1 HEAD` to see what changed in the last commit.

### also, `.gitignore`

Add files to this to tell git to ignore them.
Their changes won't be tracked, pulled, or pushed.


## meta

### aliases

step 1:
add this to your `~/.bashrc` to use `g` instead of `git`:
```bash
alias g='git'
```

step 2:
add this to your `~/.gitconfig` for more handy aliases:
```bash
[alias]
    s = status
    p = pull
    d = diff -w
    r = rebase -i
    c = !git add -A && git commit -m
    co = checkout
    cob = checkout -b
    cp = cherry-pick
    b = branch
    lol = log --graph --oneline --decorate --color --all
```

step 3: type `g p` and `g c "message"` like a legit hacker


### SSH instead of https

Never type your password again!
https://help.github.com/en/github/authenticating-to-github/connecting-to-github-with-ssh
