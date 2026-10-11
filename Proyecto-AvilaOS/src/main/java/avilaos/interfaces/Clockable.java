/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package avilaos.interfaces;

/**
 *Componente que reacciona al reloj global (CPU, controlador de E/S, etc.)
 * @author Antonella Ruiz
 */
public interface Clockable {
    
    // se invoca en cada ciclo del reloj
    void onTick(long cycle);

}
