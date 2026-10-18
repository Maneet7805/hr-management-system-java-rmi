# Contributing

This is a small group project with a hard deadline, so the rules are short. Follow them and merges stay painless.

## 1. Golden rules

1. **Never push directly to `main`.** `main` must always compile and run.
2. **One branch per task, one pull request (PR) per branch.**
3. **Every PR needs one teammate's approval** before it is merged.
4. **Change the shared interface only by agreement.** `HRMService` and the data classes in `src/common/` are used by everyone. Raise an issue and tell the group first.
5. **Never commit secrets:** passwords, keystores (`*.jks`, `*.p12`), `.env` files, database files with real data. Use the `.example` pattern (for example `config.example.properties`).

## 2. Workflow

```bash
git checkout main
git pull
git checkout -b feature/leave-application      # new branch from latest main
# ... work, commit often ...
git push -u origin feature/leave-application
```

Then open a PR on GitHub, fill in the template, and ask a teammate to review it. After approval, use **Squash and merge** and delete the branch.

Before you start work each day: `git checkout main && git pull`, then `git merge main` into your branch if it has moved on.

## 3. Branch names

`type/short-description`, lowercase, hyphens.

| Type | Use for | Example |
|---|---|---|
| `feature/` | New functionality | `feature/hr-report` |
| `fix/` | Bug fixes | `fix/leave-balance-race` |
| `docs/` | Report notes, README | `docs/protocols-section` |
| `test/` | Tests | `test/leave-service` |
| `chore/` | Build, config | `chore/gitignore` |

## 4. Commit messages

Short imperative summary, 72 characters or fewer, optionally with a body.

```
Add leave application approval to HRMService

HR can now approve or reject pending applications. Balance is deducted
inside one database transaction.
```

Good: "Fix duplicate IC check on registration". Bad: "stuff", "changes", "final final v2".

## 5. Code style (Java)

- Follow standard Java conventions: `PascalCase` classes, `camelCase` methods and fields, `UPPER_SNAKE_CASE` constants.
- 4-space indentation, no tabs (see `.editorconfig`).
- Comment *why*, not *what*. Every public method on the remote interface gets a Javadoc line.
- Catch `RemoteException` explicitly; never swallow exceptions silently.
- **Client threading:** never call the server from the Swing event thread. Use `SwingWorker`.
- **Server thread-safety:** any shared state (for example leave balances) must be protected by synchronization or a database transaction.
- Use `PreparedStatement` for every SQL query. No string-built SQL.
- Hash passwords; never store or log plain text.

## 6. Pull request checklist

- [ ] Branch is up to date with `main` and the project compiles
- [ ] I ran the code and tested my change
- [ ] I added or updated tests / test cases where relevant
- [ ] No secrets or personal data committed
- [ ] The PR is small enough to review in 15 minutes

## 7. Reviewing

Review within 24 hours if you can. Check that it works, that it follows the rules above, and that you understand it. Everyone must understand their own code because the individual report sections and any viva depend on it. Ask questions instead of just approving.

## 8. Issues and task tracking

Create an issue for each task (use the templates) and assign an owner. Reference it in the PR with `Closes #12`. The issue list is the evidence for the Gantt chart and the workload matrix in the report, so keep it honest.

## 9. Academic integrity

- Write your own individual sections (Implementation, Testing) in your own words.
- Cite every external source in APA. No blogs or forums as references.
- If you use an AI tool for code, be sure your module permits it, and make sure you can explain every line you submit.
