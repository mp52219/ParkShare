package hr.fer.proinz.proggers.parkshare.repo;

import hr.fer.proinz.proggers.parkshare.model.ClientReservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ClientReservationRepository extends JpaRepository<ClientReservation, Integer> {

    List<ClientReservation> findAllByOwnerUserIdAndParkingSpotNumber(Integer id, Integer parkingSpotNumber);
}