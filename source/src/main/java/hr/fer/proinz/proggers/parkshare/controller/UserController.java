package hr.fer.proinz.proggers.parkshare.controller;

import hr.fer.proinz.proggers.parkshare.dto.CreateParkingDTO;
import hr.fer.proinz.proggers.parkshare.dto.MessageDTO;
import hr.fer.proinz.proggers.parkshare.dto.RegisterFormDTO;
import hr.fer.proinz.proggers.parkshare.dto.UserDTO;
import hr.fer.proinz.proggers.parkshare.model.Parking;
import hr.fer.proinz.proggers.parkshare.model.ParkingOwner;
import hr.fer.proinz.proggers.parkshare.model.Parking;
import hr.fer.proinz.proggers.parkshare.model.UserModel;
import hr.fer.proinz.proggers.parkshare.repo.ClientRepository;
import hr.fer.proinz.proggers.parkshare.repo.ParkingOwnerRepository;
import hr.fer.proinz.proggers.parkshare.repo.ParkingRepository;
import hr.fer.proinz.proggers.parkshare.repo.UserRepository;
import hr.fer.proinz.proggers.parkshare.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
public class UserController {

    UserService userService;
    UserRepository userRepository;
    ParkingOwnerRepository ownerRepository;
    ClientRepository clientRepository;
    ParkingRepository parkingRepository;

    @Autowired
    public UserController(UserService userService, UserRepository userRepository,
                          ParkingOwnerRepository ownerRepository, ClientRepository clientRepository,
                          ParkingRepository parkingRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.ownerRepository = ownerRepository;
        this.clientRepository = clientRepository;
        this.parkingRepository = parkingRepository;
    }

    @PostMapping("/")
    public ModelAndView register(RegisterFormDTO registerFormDTO, ModelMap model, HttpServletRequest request) {
        ArrayList<MessageDTO> errors = new ArrayList<>();
        ArrayList<MessageDTO> information = new ArrayList<>();
        UserModel registered;
        try {
            registered = userService.registerNewUser(registerFormDTO);
            if (registered.getType().equals("client")) {
                userService.sendMail(registered, getSiteURL(request));
            } else {
                ownerRepository.findById(registered.getId()).ifPresent((owner) -> {
                    owner.setIban(registerFormDTO.getIban());
                    ownerRepository.save(owner);
                });
            }
        } catch (ResponseStatusException e) {
            model.addAttribute("registerForm", new RegisterFormDTO());
            errors.add(new MessageDTO("Registration failed!",
                    "Account with given username or email already exists."));
            model.addAttribute("errors", errors);
            return new ModelAndView("index", model);
        }
        model.addAttribute("registerForm", new RegisterFormDTO());
        information.add(new MessageDTO("Registration successful!",
                registered.getType().equals("owner") ?
                        "Please wait for an administrator to confirm your registration." :
                        "To login, please confirm your account by email."));
        model.addAttribute("information", information);
        return new ModelAndView("index", model);
    }

    private String getSiteURL(HttpServletRequest request) {
        return request.getRequestURL().toString();
    }

    @GetMapping("/confirm")
    public String verifyUser(@Param("code") String code, Model model) {
        if (userService.verify(code)) {
            return "redirect:/?verifySuccess=true";
        }
        return "redirect:/?verifyFailure=true";
    }

    @GetMapping("/")
    public String showRegistrationForm(Model model, Authentication auth) {
        if (auth != null) {
            return "redirect:/loginRouter";
        }
        model.addAttribute("registerForm", new RegisterFormDTO());
        return "index";
    }

    @GetMapping("/profile")
    public String showUserDetails(Model model, Authentication auth) {
        boolean loggedIn;
        loggedIn = auth != null;
        model.addAttribute("loggedIn", loggedIn);
        assert auth != null;
        UserDTO currentUser = userService.UserToDTO(userRepository.findByEmail(auth.getName()));
        model.addAttribute("user", currentUser);
        if(currentUser.isOwner()) {
            boolean hasParking = false;
            Optional<Parking> ownerParking = parkingRepository.findById(currentUser.getId());
            if(ownerParking.isPresent()) {
                hasParking = true;
            }
            model.addAttribute("hasParking", hasParking);
        }
        return "userpage";
    }


    @PostMapping("/profile")
    public ModelAndView editUserDetails(UserDTO updatedUser, ModelMap model, Authentication auth) {
        ArrayList<MessageDTO> errors = new ArrayList<>();
        ArrayList<MessageDTO> information = new ArrayList<>();
        UserModel userModel;
        //TODO make it pretty lmao
        try {
            userModel = userService.updateUser(updatedUser, false, userRepository.findByEmail(auth.getName()).getId());
        } catch (InvalidParameterException i) {
            errors.add(new MessageDTO("Update failed!",
                    "Old password isn't correct."));
            model.addAttribute("errors", errors);
            UserModel currentUserModel = userRepository.findByEmail(auth.getName());
            model.addAttribute("user", userService.UserToDTO(currentUserModel));
            return new ModelAndView("userpage", model);
        } catch (ResponseStatusException e) {
            errors.add(new MessageDTO("Update failed!",
                    "Account with given username already exists."));
            model.addAttribute("errors", errors);
            UserModel currentUserModel = userRepository.findByEmail(auth.getName());
            model.addAttribute("user", userService.UserToDTO(currentUserModel));
            return new ModelAndView("userpage", model);
        }

        model.addAttribute("user", userService.UserToDTO(userModel));
        information.add(new MessageDTO("Success!", "Your data has been updated."));
        model.addAttribute("information", information);
        System.out.println(updatedUser);
        updatedUser.setRole(userModel.getType());
        return new ModelAndView("userpage", model);
    }

    @PostMapping("/profile/createParking")
    public ModelAndView createParking(CreateParkingDTO parking, ModelMap model, Authentication auth) {
        ArrayList<MessageDTO> errors = new ArrayList<>();
        ArrayList<MessageDTO> information = new ArrayList<>();
        UserModel currentUserModel = userRepository.findByEmail(auth.getName());
        try {
            Integer id = userRepository.findByEmail(auth.getName()).getId();
            if (!parkingRepository.existsById(id)) {
                Parking newParking = new Parking();
                newParking.setParkingName(parking.getParkingName());
                newParking.setHourlyPrice(parking.getHourlyPrice());
                newParking.setParkingPhoto(parking.getParkingPhoto());
                newParking.setDescription(parking.getDescription());
                newParking.setId(id);
          // TODO add x and y coordinates in newParking
                parkingRepository.save(newParking);
                information.add(new MessageDTO("Parking created successfuly", ""));
                model.addAttribute("information", information);
                model.addAttribute("user", userService.UserToDTO(currentUserModel));
                return new ModelAndView("createParkingSpot", model);
            } else {
                errors.add(new MessageDTO("Parking already exists", "You can have only one parking"));
                model.addAttribute("errors", errors);
            }
        } catch(Exception exc) {
            errors.add(new MessageDTO("Error happend", exc.getMessage()));
            model.addAttribute("errors", errors);
            model.addAttribute("user", userService.UserToDTO(currentUserModel));
            return new ModelAndView("userpage", model);
        }
        model.addAttribute("user", userService.UserToDTO(currentUserModel));
        return new ModelAndView("userpage", model);
    }


    @GetMapping("/profile/createParking")
    public String createParkingForm(Model model, Authentication auth) {
        UserModel currentUser = userRepository.findByEmail(auth.getName());
        if (currentUser.isOwner()) {
            model.addAttribute("parking", new CreateParkingDTO());
            return "createParking";
        } else {
           return "redirect:/profile";
        }
    }
}
