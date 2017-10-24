(ns snakeweek.drawing
  (:require [play-cljs.core :as p]
            [snakeweek.assets :as assets]
            [cljs.core.async :refer [promise-chan put! <!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(def UNIT 12)

(def font-atom (atom nil))
(def font-ready? (atom false))

(defmethod p/draw-sketch! :no-smooth [game ^js/p5 renderer content parent-opts]
  (let [[command & children] content]
    (.push renderer)
    (.noSmooth renderer)
    (p/draw-sketch! game renderer children parent-opts)
    (.pop renderer)))

(defmethod p/draw-sketch! :menu-entry [game ^js/p5 renderer content parent-opts]
  (let [[command opts & children] content
        {:keys [x y value size active?] :as opts}
        (p/update-opts opts parent-opts p/text-defaults)]

    (let [font (assets/get-asset "ArcadeAlternate.ttf")]
      (doto renderer
        (.textSize size)
        (.textFont font)
        (.textAlign (p/halign->constant renderer :center)
                    (p/valign->constant renderer :center))
        (.text value x y))
      (let [bounds (.textBounds font value x y)
            bx (.-x bounds) by (.-y bounds)
            bw (.-w bounds) bh (.-h bounds)]
        (when active?
          (p/draw-sketch! game renderer children
                          (dissoc (p/update-opts opts {:x (- bx x) :y (- by y)} {})
                                  :value)))
        ))))

(defn draw-cell [state]
  (fn [[x y]]
    [:fill {:color "white"}
     [:rect {:x (* UNIT x) :y (* UNIT y) :width UNIT :height UNIT}]]))

(defn draw-food [[x y ttl]]
  [:fill {:color "yellow"}
   [:rect {:x (* UNIT x) :y (* UNIT y) :width UNIT :height UNIT}]])

(defn draw-background [color]
  [:fill {:color color}
   [:rect {:x 0 :y 0 :width (.-innerWidth js/window) :height (.-innerHeight js/window)}]])

(defn draw-board [{:keys [width height]}]
  [:stroke {:color "white"}
   [:fill {:color "black"}
    [:rect {:x -1 :y -1
            :width (+ 2 (* width UNIT))
            :height (+ 2 (* height UNIT))}]]])

(defn draw-score [{:keys [score height]}]
  [:fill {:color "white"}
   [:text {:value (str "Score: " score)
           :x 20 :y (+ 20 (* height UNIT))
           :font (assets/get-asset "ArcadeAlternate.ttf")
           :size 14}]]){}

(defn draw-hud [{:keys [paused width height] :as state}]
  (if paused
    [:fill {:color "red"}
     [:text {:value "PAUSED" :size 20
             :font (assets/get-asset "ArcadeAlternate.ttf")
             :x (- (/ (* UNIT width) 2) 40)
             :y (/ (* UNIT height) 2)}]]
    [])
  )

(defn draw-walls [wall-coords]
  (for [[x y] wall-coords]
    [:fill {:color "grey"}
     [:image {:x (* UNIT x) :y (* UNIT y)
              :value (assets/get-asset "wall.png")
              :width UNIT :height UNIT}]]
    ))

(defn draw-credits []
  [:div {:x (/ (.-innerWidth js/window) 2)
         :y 0
         :font (assets/get-asset "ArcadeAlternate.ttf")
         :size 18 :halign :right :width 300}
   [:no-smooth
    [:image {:x -150 :y 20 :name "logo.png" :width 300 :height 90}]]
   [:div {:halign :right :x -10 :y 170}
    [:fill {:color "#666"}
     [:text {:y 0 :value "Programming:"}]
     [:text {:y 40 :value "Graphics:"}]
     [:text {:y 80 :value "Music:"}]
     ]]
   [:div {:halign :left :x 10 :y 170}
    [:fill {:color "#fff"}
     [:text {:y 0 :value "Freddy"}]
     [:text {:y 40 :value "Freddy"}]
     [:text {:y 80 :value "Freddy"}]
     ]]
   [:fill {:color "#fff"}
    [:text {:halign :center :x -20
            :y 320
            :value "Press any key to go back"}
     ]]]
  )


(defn create-menu [ct x y items active-id]
  (defn active? [i] (= active-id i))

  (let [frame (mod (int (/ ct 100)) 5)]
    [:rect {:x x :y y :width 0 :height 0}
     (for [[i item] (seq (map-indexed vector items))]
       [:fill {:color (if (active? i) "white" "#666")}
        [:menu-entry {:value item :x 0 :y (* i 30) :size 18
                      :active? (active? i)}
         [:no-smooth
          [:animation {:duration 100}
           [:image {:name "arrow.png" :x -18 :y 2 :sx 0 :swidth 5 :width 10 :height 10}]
           [:image {:name "arrow.png" :x -18 :y 2 :sx 5 :swidth 5 :width 10 :height 10}]
           [:image {:name "arrow.png" :x -18 :y 2 :sx 10 :swidth 5 :width 10 :height 10}]
           [:image {:name "arrow.png" :x -18 :y 2 :sx 15 :swidth 5 :width 10 :height 10}]
           [:image {:name "arrow.png" :x -18 :y 2 :sx 20 :swidth 5 :width 10 :height 10}]
           ]]]]
       )]))
