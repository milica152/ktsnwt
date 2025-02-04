package rs.ac.uns.ftn.ktsnwt.service.tickets;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import rs.ac.uns.ftn.ktsnwt.common.TimeProvider;
import rs.ac.uns.ftn.ktsnwt.dto.PricingSeatDTO;
import rs.ac.uns.ftn.ktsnwt.dto.TicketsToReserveDTO;
import rs.ac.uns.ftn.ktsnwt.exception.ApiRequestException;
import rs.ac.uns.ftn.ktsnwt.exception.ResourceNotFoundException;
import rs.ac.uns.ftn.ktsnwt.model.*;
import rs.ac.uns.ftn.ktsnwt.model.enums.EventStatus;
import rs.ac.uns.ftn.ktsnwt.model.enums.SectorType;
import rs.ac.uns.ftn.ktsnwt.repository.TicketRepository;
import rs.ac.uns.ftn.ktsnwt.service.eventday.EventDayService;
import rs.ac.uns.ftn.ktsnwt.service.pricing.PricingService;

import java.util.List;

@Service
public class TicketsServiceImpl implements TicketsService {

    @Autowired
    private EventDayService eventDayService;

    @Autowired
    private PricingService pricingService;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private TimeProvider timeProvider;


    @Override
    public List<Ticket> getUsersTickets(int page) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return ticketRepository.getByUserId(user.getId(), PageRequest.of(page, 5)).toList();
    }

    @Override
    public void reserveTickets(TicketsToReserveDTO ticketsInfo) {
        EventDay eventDay = eventDayService.getEventDay(ticketsInfo.getEventDayId());
        Event event = eventDay.getEvent();

        if (eventDay.getStatus() != EventStatus.ACTIVE)
            throw new ApiRequestException("You can't buy tickets for this event day because it is not active.");

        if (ticketsInfo.getSeats().size() > event.getTicketsPerUser())
            throw new ApiRequestException("You can't buy more than " + event.getTicketsPerUser() + " tickets at once.");


        if (event.getStartDate().before(timeProvider.addDaysToDate(timeProvider.nowTimestamp(), event.getPurchaseLimit())))
            throw new ApiRequestException("You can't buy a ticket because purchase date has expired.");

        ticketsInfo.getSeats().stream().forEach(seat-> createTicket(seat, eventDay));
    }

    private void createTicket(PricingSeatDTO seat, EventDay eventDay) {
        Pricing pricing = pricingService.getPricing(seat.getPricingId());
        Sector sector = pricing.getSector();

        if (ticketRepository.getTicketsCountByPricingAndSector(pricing.getId(), sector.getId()) + 1 > sector.getCapacity())
            throw new ApiRequestException("Sector with id " + sector.getId() + " doesn't have enough space.");

        if (sector.getType() == SectorType.FLOOR) {
            saveTicket(0, 0, pricing, eventDay);
        } else { // SectoryType.SEATS
            saveTicketForSeatingSector(seat, pricing, eventDay, sector);
        }
    }

    private boolean isSeatFree(EventDay eventDay, PricingSeatDTO seat) {
        Ticket ticket = ticketRepository.getByRowAndColumnAndEventDayId(seat.getRow(), seat.getSeat(), eventDay.getId());
        return ticket == null;
    }

    private void saveTicketForSeatingSector(PricingSeatDTO seat, Pricing pricing, EventDay eventDay, Sector sector) {
        if (seat.getRow() > sector.getNumRows() || seat.getRow() < 0)
            throw new ApiRequestException("Row " + seat.getRow() + " doesn't exist in this sector");

        if (seat.getSeat() > sector.getNumColumns() || seat.getSeat() < 0)
            throw new ApiRequestException("Seat " + seat.getSeat() + " doesn't exist in this sector");

        if (!isSeatFree(eventDay, seat))
            throw new ApiRequestException("Seat " + seat.getSeat() + " in row " + seat.getRow() + " is not free.");

        saveTicket(seat.getRow(), seat.getSeat(), pricing, eventDay);
    }

    private void saveTicket(int row, int seat, Pricing pricing, EventDay eventDay) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Ticket ticket = new Ticket();
        ticket.setPurchased(false);
        ticket.setRow(row);
        ticket.setColumn(seat);
        ticket.setPricing(pricing);
        ticket.setEventDay(eventDay);
        ticket.setDatePurchased(timeProvider.nowTimestamp());
        ticket.setUser(user);

        ticketRepository.save(ticket);
    }

    @Override
    public void cancelTicket(Long id) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Ticket ticket = ticketRepository.findById(id).orElse(null);

        if (ticket == null)
            throw new ResourceNotFoundException("Ticket with ID " + id + " doesn't exist.");

        if (!ticket.getUser().equals(user))
            throw new ApiRequestException("You can't cancel other users ticket");

        if (ticket.isPurchased())
            throw new ApiRequestException("You can't cancel this ticket because it's not refundable.");

        Event event = ticket.getEventDay().getEvent();
        if (event.getStartDate().before(timeProvider.addDaysToDate(timeProvider.nowTimestamp(), event.getPurchaseLimit())))
            throw new ApiRequestException("You can't cancel this ticket because time has run out for cancellation.");

        ticketRepository.delete(ticket);
    }

}
