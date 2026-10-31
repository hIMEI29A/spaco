
# Contributing Guide (for AI Agents)

This document defines the Git workflow and conventions that AI agents must follow when working on this project.

## Branching

- Always start a new task branch from the `main` branch.

- Create a feature branch using the following naming template:[user]-[issue-number]-[issue-tag]-[kebab-case-description]

```text
Examples:

- `alice-42-bug-fix-login-validation`

- `bob-15-feat-add-export-button`
```

- If the work is **not** linked to any issue, replace `[issue-number]-[issue-tag]` with the slug `no-issue`:[user]-no-issue-[kebab-case-description]

```text
Example: `alice-no-issue-update-dependencies`
```

## Commits

- Use a brief, single-line commit message.

- Keep the message focused and descriptive. Do not write multi-line or verbose commit messages.

## Pull Requests

- If the PR closes an issue, begin the PR description with:Closes: #[issue-number]text- Provide a concise overview of the problem and the changes made. Avoid boilerplate text.

- Check the list of available labels in the repository and suggest the most appropriate ones. Always ask the user to confirm the suggested labels before applying them.

- If the branch was created from a branch other than `main`, set the PR base branch accordingly (do not default to `main`).

## Issues

- When creating or commenting on an issue, clearly note that the content was produced in collaboration with an AI agent.

```text
Example: `Generated with OpenCode`
```

## Releases

- Create a release by opening a Pull Request from `dev` → `main`.

- On the merge commit of that PR, create a tag in the format `vX.Y.Z-alpha`.

- In the same release PR, update the “Current version” section in `README.md` to match
