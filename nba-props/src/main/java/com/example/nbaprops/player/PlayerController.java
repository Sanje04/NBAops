package com.example.nbaprops.player;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController 
@RequestMapping("/api/players")
public class PlayerController {
    
    @GetMapping
    public List<Player> getPlayer(){
        return List.of(
            new Player (1L, "LeBron James", "Lakers"),
            new Player (2L, "Stephen Curry", "Warriors")
        );
    }
}