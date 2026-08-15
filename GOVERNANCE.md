# Odoc governance

**Status:** proposed foundation policy; project-owner approval is required before
roadmap package `P0-001` is complete.

Odoc begins as a founder-led open-source project and is intended to evolve toward
earned, transparent community maintenance. This document describes decision rights;
it does not create a legal entity or transfer copyright or trademark ownership.

## Roles

### Users and contributors

Anyone may use the public project channels, report issues, participate in design,
and contribute under [CONTRIBUTING.md](CONTRIBUTING.md). Contribution volume does
not create authority over another contributor or guarantee that a proposal ships.

### Reviewers

Reviewers are contributors trusted to triage or review in a documented area. They
may recommend approval but do not merge or release unless they are also maintainers.

### Maintainers

Maintainers may triage, approve, and merge within named areas; operate community
channels; and help prepare releases. They are expected to review consistently,
protect user data and compatibility, disclose conflicts, mentor contributors, and
follow the security embargo process.

Maintainer status is earned through sustained, constructive work and sound judgment,
not purchased or granted automatically to an employer. The project owner appoints
the initial maintainers after public nomination or documented rationale. Once three
unconflicted maintainers exist, adding or removing a maintainer requires two-thirds
of active unconflicted maintainers and project-owner confirmation during the
founder-led stage, except that the owner has no confirmation or veto when the owner
is the subject of the decision.

For bootstrap only, the project owner is also the sole initial maintainer. “Active”
means having reviewed, merged, triaged, released, or participated in a recorded project
decision in the preceding 90 days, excluding a published leave of absence. The owner
keeps a public maintainer roster with responsibility areas; nobody has maintainer
authority merely because a document refers generically to maintainers.

Current proposed bootstrap roster:

| Maintainer | Responsibility | Status |
|---|---|---|
| [`@StrangeQuark`](https://github.com/StrangeQuark) | Project administration and all areas until responsibility is delegated | Proposed initial maintainer; effective only when this foundation bundle is approved |

### Project owner

The initial project owner is the GitHub account
[`@StrangeQuark`](https://github.com/StrangeQuark). The project owner controls
official releases, repository administration, foundation policy approval, use of
official project branding, and the final decision when consensus cannot be reached.
The owner cannot relicense copyright owned by contributors without the rights and
process required by law and this policy.

## How decisions are made

Routine, reversible implementation decisions use issue and pull-request review.
Maintainers seek reasoned consensus: address technical objections, record tradeoffs,
and prefer evidence from prototypes, tests, user research, and operations. Silence
is not consent for a material decision.

Cross-project material proposals are announced in a public `odoc` issue labeled
`governance` or `architecture` and linked from affected repositories. “Consensus”
means that active, unconflicted maintainers have had the full review window and no
reasoned blocking objection remains; it does not require unanimity from inactive or
recused people. The owner fallback below resolves a documented remaining objection.

An Architecture Decision Record and an announced review window are required for:

- public API, storage, editor-schema, authentication, authorization, encryption,
  search, integration, extension, or deployment architecture;
- a breaking compatibility or data-retention change;
- a new required external service or proprietary dependency;
- a release-stage, support, license, governance, or public product-claim change.

The normal review window is at least seven calendar days for a material reversible
decision and fourteen days for governance, licensing, irreversible data format, or
public cryptographic claims. A maintainer may shorten it for an actively exploited
vulnerability or data-loss fix, documenting why and scheduling retrospective review.

If consensus is not possible, the project owner publishes a decision with the
alternatives, objections, evidence, and revisit condition. Decisions may be appealed
with materially new evidence; repeated restatement alone does not reopen them.

## Required review

Authors do not provide the sole approval for their own change.

The one-time exception is adoption of the `P0-001` foundation bundle: no community
maintainer or contributor body existed before it. The initial project owner may adopt
that bundle explicitly after its owner-controlled choices and operational reporting
channels are resolved. The exception expires immediately upon adoption and cannot be
used for later license, governance, security-claim, or support-policy changes.

Until a second maintainer is appointed, a narrow transition rule applies: an
owner-authored routine change needs one public, unconflicted external reviewer approval;
an owner-authored change in the two-review row needs two such reviewers, including one
with demonstrated responsibility-area expertise. The owner may then merge but cannot
represent their own review as an approval. This rule expires when two maintainers exist
and in every event before the first tagged release. It never substitutes for an
independent cryptography, license, conduct, or other specialist gate.

| Change | Minimum approval during founder-led stage |
|---|---|
| Routine scoped change | One unconflicted maintainer |
| Public contract, migration, authorization, deployment, or release automation | Two reviewers, including one responsible-area maintainer |
| Cryptographic protocol or claim | Responsible maintainer plus independent qualified security/cryptography review |
| Project license, CLA, dual licensing, or governance | Maintainer consensus, project-owner approval, contributor-rights analysis, and public rationale |
| Official release | Release maintainer and project owner after release gates pass |

No reviewer may waive an acceptance gate merely because another package plans to
repair the same issue later.

The owner consensus fallback resolves ordinary product/architecture choices only. It
cannot replace an approval, independent assessment, contributor-rights analysis, recusal,
or release gate required by the table above.

An “independent qualified” reviewer has demonstrated relevant professional or research
experience, did not author the design, has no vendor/employment/financial interest that
could reasonably determine the outcome, and is free to publish a pass/fail scope
statement. The decision record identifies the reviewer and evidence without exposing
private report material.

## Conflicts of interest

Anyone participating in a decision discloses employment, financial interest,
vendor relationship, close personal relationship, or other circumstance that a
reasonable person could view as affecting judgment. A conflicted person may supply
facts but must not be the deciding vote or sole reviewer. The decision record names
the recusal without exposing unnecessary personal information.

## Security and private decisions

Vulnerability details, reporter identity, credentials, and exploit material follow
[SECURITY.md](SECURITY.md) and may be discussed privately until coordinated
disclosure. A private fix still receives review by the smallest qualified group,
and the public record is completed after disclosure without exposing secrets.
Private channels must not be used to hide ordinary product or governance decisions.

## Conduct and moderation

Maintainers enforce [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) consistently. A
maintainer named in a report is recused. Reports involving the project owner require
an independent unconflicted moderator; a dedicated private conduct contact and
appeal path must be published before accepting outside contributions or opening public
community chat/events. The proposed policy is not operational until those contacts are
named and monitored.

## Inactivity, removal, and succession

A maintainer may step down at any time. After six months without project activity,
the project may move a maintainer to emeritus status after private notice and a
reasonable response period; access can be restored through the normal appointment
process. Access may be suspended immediately to contain a credential or safety risk.

Removal for misconduct, repeated security-policy violations, undisclosed conflicts,
or sustained failure of responsibilities requires documented facts, recusal of
involved decision-makers, an opportunity to respond when safe, and the vote described
under Roles. Before three maintainers exist, removing a non-owner maintainer requires
the owner plus the independent conduct/appeal moderator; neither may be the reporter or
subject. If the owner is the subject, the independent moderator appoints two unconflicted
external reviewers to decide project-role suspension and publish a privacy-preserving
outcome. The owner cannot veto that outcome, but this policy cannot technically transfer
an individually owned repository: if required access cannot be removed, contributions
and releases pause while maintainers migrate the official community work to an
organization-controlled repository. Conduct reports remain confidential to the extent
possible.

The project owner must document at least two release administrators and recovery of
repository, signing, domain, package, and infrastructure access before 1.0. A future
governance ADR will replace single-owner fallback once the maintainer community and
legal home can sustain it.

## Amendments

Governance changes are proposed publicly, include migration and dissent notes, and
follow the material-decision window. Each approved change records its effective date.
Emergency access-control changes may take effect immediately but receive retrospective
review within seven days.
