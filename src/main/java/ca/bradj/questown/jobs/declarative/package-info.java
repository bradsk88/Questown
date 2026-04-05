/**
 * Declarative Job Package - Canonical Implementation
 * ===================================================
 *
 * This package contains the canonical implementation of declarative jobs.
 * Classes here are intentionally package-private to enforce consolidation
 * of job logic and prevent external code from modifying core behavior.
 *
 * DESIGN PRINCIPLES:
 *
 * 1. ONE WAY TO DO THINGS
 *    DeclarativeJob behavior should be defined exactly once, here.
 *    Avoid utility classes, static helpers, and interfaces that allow
 *    multiple implementations of the same logic.
 *
 * 2. CONCRETE OVER ABSTRACT
 *    Prefer concrete types (e.g., DeclarativeJobTickerDependencies) over
 *    interfaces and generics. This makes the code path explicit and
 *    prevents accidental divergence in behavior.
 *
 * 3. INTEGRATION OVER UNIT TESTING
 *    Test jobs via integration tests that exercise the full flow.
 *    Avoid unit tests that require mocking/stubbing core job internals,
 *    as these can drift from real behavior.
 *
 * 4. GRADUAL CONSOLIDATION
 *    Logic scattered in JobsClean, ContainersClean, *WI classes, etc.
 *    should be migrated here over time - assume they are specific to 
 *    "declarative" jobs. New functionality should be added directly 
 *    to classes in this package.
 *
 * HISTORICAL CONTEXT:
 * The codebase evolved with job logic spread across many utility classes
 * to enable isolated unit testing. While well-intentioned, this led to:
 * - Multiple implementations of similar logic
 * - Unclear canonical behavior
 * - Complex dependency graphs
 *
 * This package represents a move toward consolidation and simplicity.
 */
package ca.bradj.questown.jobs.declarative;