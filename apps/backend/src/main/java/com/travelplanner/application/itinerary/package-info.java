/**
 * Itinerary persistence and read access (task 28, UC-C3-01/02).
 *
 * <p>The orchestration half of C3: load via ports, call
 * {@code domain.algorithm.scheduling.ItineraryScheduler}, persist the result. Feasibility is never
 * decided here, and no LLM or route provider is called from this package — task 30 narrates a plan
 * that already exists, task 29 fills in the legs between its items.
 */
package com.travelplanner.application.itinerary;
