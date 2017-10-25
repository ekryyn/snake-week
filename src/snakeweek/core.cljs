(ns snakeweek.core
  (:require [play-cljs.core :as p]
            [cljs.core.async :refer [take!]]
            [snakeweek.assets :as assets]
            [snakeweek.maps :as maps]
            [snakeweek.drawing :as d]
            [goog.events :as events]))

(enable-console-print!)

(defonce game (p/create-game (.-innerWidth js/window) (.-innerHeight js/window)))
(defonce state (atom {}))
(defonce pressed-keys (atom #{}))
(defonce menu-screen-atom (atom nil))
(defonce levels (atom []))


(defn coords= [[x y] [fx fy]]
  (and (= x fx) (= y fy)))

(defn update-snake [{:keys [snake dir level]}]
  (defn adjust [[x y from to]]
    (let [width (:width level)
          height (:height level)
          x (cond (< x 0) (dec width)
                  (>= x width) 0
                  :else x)
          y (cond (< y 0) (dec height)
                  (>= y height) 0
                  :else y)]
      [x y from to]))

  (defn update-head [[x y from to]]
    [x y to dir])

  (defn new-head [[x y prev-from prev-to]]
    (case dir
      :north [x (dec y) prev-to dir]
      :east  [(inc x) y prev-to dir]
      :south [x (inc y) prev-to dir]
      :west  [(dec x) y prev-to dir]))

  (let [[head & tail] snake]
    (cons (-> head new-head adjust)
          (drop-last (cons (update-head head) tail)))))

(defn die [{:keys [snake walls] :as state}]
  (let [[head & tail] snake]
    (if (or (some #(coords= head %) tail)
            (some #(coords= head %) walls))
        (assoc state :dead true)
        state)))

(defn grow [snake]
  (let [tail (last snake)]
    (conj (vec snake) tail))
  )

(defn get-age [[_ _ birth] current-time]
  (- current-time birth))

(defn spawn-food [{:keys [width height current-time snake walls] :as state}]
  (let [x (rand-int width)
        y (rand-int height)]
    (if (or (some #(coords= [x y] %) walls)
            (some #(coords= [x y] %) snake))
      (spawn-food state)
      (assoc state
             :food [x y current-time]
             :last-spawn current-time))
    ))

(defn get-score [age]
  (int (/ (+ 1000 (- 6000 age)) 10)))

(defn eat-food
  [{:keys [snake head food current-time width height] :as state}]
  (let [head (first snake)]
    (if (coords= head food)
      (-> state
          spawn-food
          (update :snake grow)
          (update :speed #(max (dec %) 5))
          (update :score + (get-score (get-age food current-time))))
      state
      )))

(defn move-snake
  [{:keys [snake dir last-move current-time speed] :as state}]
  (if (> (- current-time last-move) speed)
      (let [new (update-snake state)]
        (-> state
            (assoc :snake new
                   :last-move current-time)
            (die)
            (eat-food)
            ))
      state))

(defn handle-input [state]
  (let [keys @pressed-keys
        dir (:dir state)
        change-dir #(assoc state :dir %)]
   (cond
     (and (contains? keys 37) (not= dir :east)) (change-dir :west)
     (and (contains? keys 38) (not= dir :south)) (change-dir :north)
     (and (contains? keys 39) (not= dir :west)) (change-dir :east)
     (and (contains? keys 40) (not= dir :north)) (change-dir :south)
     :else state
     ))
  )

(defn update-food
  [{:keys [food current-time width height]:as state} ttl]
  (let [[x y birth] food]
    (if (> (get-age food current-time) ttl)
      (spawn-food state)
      state)))

(defn update-game [{:keys [dead paused] :as state} delta]
  (if (or dead paused)
    state
    (-> state
        (handle-input)
        (update-food 10000)
        (move-snake)
        )))

(defn init-state! [level]
  (let [ct (p/get-total-time game)]
    (reset! state
            (into level
                  {:snake [[6 3 :east :east]
                           [5 3 :east :east]
                           [4 3 :east :east]]
                   :speed 80
                   :score 0
                   :level level
                   :paused false
                   :last-spawn ct
                   :last-move ct
                   :current-time ct
                   :dir :east}))))

(defn new-game! [level]
  (reset! pressed-keys #{})
  (init-state! level)
  (println "NEW GAME!")
  (swap! state spawn-food)
  )

(defn on-key-down [state key-code]
  (if (= key-code 32) ; SPACE BAR
    (update state :paused not)
    state))

(defn on-key-up [state key-code]
  state)

(def main-screen
  (reify p/Screen
    (on-show [this]
      (doto js/window
        (events/removeAll "keydown")
        (events/removeAll "keyup")
        (events/listen "keydown"
                       (fn [e]
                         (swap! state on-key-down (.-keyCode e))
                         (swap! pressed-keys conj (.-keyCode e))))
        (events/listen "keyup"
                       (fn [e]
                         (swap! state on-key-up (.-keyCode e))
                         (swap! pressed-keys disj (.-keyCode e)))))
      (new-game! (get @levels 0))
      (.play (assets/get-asset "snake.mp3"))
      )
    (on-hide [this]
      (.stop (assets/get-asset "snake.mp3")))
    (on-render [this]
      (swap! state assoc :current-time (p/get-total-time game))
      (swap! state update-game (p/get-delta-time game))
      (when (:dead @state) (p/set-screen game @menu-screen-atom))
      (p/render
       game
       [(d/draw-background "#000")
        [:rect ; container
         {:x (- (/ (p/get-width game) 2) (/ (* d/UNIT (:width @state)) 2))
          :y 100}
         (d/draw-board @state)
         (d/draw-score @state)
         [:no-smooth
          (let [[head & tail] (:snake @state)
                body (butlast tail)
                tail (last tail)]
            [
             (map d/draw-cell body)
             (d/draw-tail tail)
             (d/draw-head head)
             ])
          ]
         (let [food (:food @state)]
           (when food (d/draw-food food)))
         (d/draw-hud @state)
         (d/draw-walls (:walls @state))
         ]]
       ))))

(def credits-screen
  (reify p/Screen
    (on-show [this]
      (events/removeAll js/window "keydown")
      (events/removeAll js/window "keyup")
      (events/listen js/window "keydown"
                     #(p/set-screen game @menu-screen-atom))
      )

    (on-render [this]
      (let [i (mod (int (/ (p/get-total-time game) 100)) 5)]
        (p/render
        game
        [
         (d/draw-background "#000")
         (d/draw-credits)
         ])))
    (on-hide [this])
    ))

(def loading-screen
  (reify p/Screen
    (on-show [this])
    (on-render [this]
      (let [i (mod (int (/ (p/get-total-time game) 100)) 5)]
        (p/render game
         [(d/draw-background "#000")
          [:div {:x (/ (.-innerWidth js/window) 2) :y 200}
           [:fill {:color "white"}
            [:text {:value "Loading..."
                    :size 25 :halign :center}]]]])))
    (on-hide [this])
    ))

(def menu-screen
  (let [active-id (atom 0)]
    (defn menu-action [keycode]
      (case keycode
        38 (reset! active-id (max 0 (dec @active-id)))
        40 (reset! active-id (min 2 (inc @active-id)))
        13 (case @active-id
             0 (p/set-screen game main-screen)
             2 (p/set-screen game credits-screen)
             nil)
        nil
        ))
    (reify p/Screen
      (on-show [this]
        (events/removeAll js/window "keydown")
        (events/removeAll js/window "keyup")
        (events/listen js/window "keydown"
                       #(menu-action (.-keyCode %)))
        )
     (on-hide [this])
     (on-render [this]
       (p/render
        game
        [
         (d/draw-background "#000")
         [:fill {:color "#000"}
          [:rect
           {:width 500 :height 300
            :x (- (/ (p/get-width game) 2) 250)
            :y 20}
           [:no-smooth
             [:image {:x 100 :y 0 :name "logo.png" :width 300 :height 90}]
            (d/create-menu
             (p/get-total-time game)
             250 150 ["New Game" "View Scores" "Credits"] @active-id)
            ]]]]
        ))
     )))

(do
  (p/start game)
  (p/set-screen game loading-screen)
  (take!
   (assets/load-assets! (p/get-renderer game))
   (fn [_]
     (reset! menu-screen-atom menu-screen)
     (p/set-screen game menu-screen)
     (reset! levels [(maps/load-map "maze.png")])
     )))

