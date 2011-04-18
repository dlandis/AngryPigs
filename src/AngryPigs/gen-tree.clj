
(ns AngryPig.gen-tree)

; the aim of this thing is to generate a tree
; main program expects a list of point,vector tuples that represent cyllindres making up the tree

; operative keyword: fractals

; data structure:
; node: (x y z d)  (vector with distance)
; tree: (node
;        (node
; 	(node
; 	 (node) (node) (node) (node) (node))
; 	(node
; 	 (node) (node) (node) (node) (node))
; 	(node
; 	 (node) (node) (node) (node) (node)))
;        (node
; 	(node
; 	 (node) (node) (node) (node) (node))
; 	(node
; 	 (node) (node) (node) (node) (node))
; 	(node
; 	 (node) (node) (node) (node) (node))))


(def primes [2 3 5 7 11 13 17 23 29])
(def max-depth 2)
(def I [[1 0 0]
	[0 1 0]
	[0 0 1]])
(def nums (iterate inc 0))

; generate a tree from starting position v
(defn travel [node depth]
  (let [d (last node)]
    (concat (map #(+ %1 d) (butlast node)) [(/ d (nth primes depth))])))

(defn plane [N]
  (let [point (butlast (travel N 0))]
    (concat (butlast N)
	    [(apply + (map #(* (nth N %1) (nth point %1))
			   (take (count point) nums)))])))

(defn point-on-plane [plane]
  (let [d (last plane)]
    (let [plane (reverse (sort (butlast plane)))]
      (loop [point [] i 0 dd d]
	(if (>= i (count plane)) point
	    (let [component (/ dd (nth plane i))]
	      (recur (cons component point) (inc i) (- dd component))))))))

(defn vector-between-points [a b]
  (map #(- (nth b %1) (nth a %1))
       (take (count a) nums)))

(defn length [v]
  (Math/sqrt (apply + (map #(* %1 %1) v))))

(defn normalize [v]
  (let [l (length v)]
    (map #(/ %1 l) v)))

(defn make-node [v l]
  (concat v [l]))


; taken from http://steve.hollasch.net/cgindex/math/rotvec.html
;    let
;        [v] = [vx, vy, vz]      the vector to be rotated.
;        [l] = [lx, ly, lz]      the vector about rotation
;              | 1  0  0|
;        [i] = | 0  1  0|           the identity matrix        
;              | 0  0  1|
;              
;              |   0  lz -ly |
;        [L] = | -lz   0  lx |
;              |  ly -lx   0 |
;
;        d = sqrt(lx*lx + ly*ly + lz*lz)
;        a                       the angle of rotation
;
;    then
;
;   matrix operations gives:
;
;    [v] = [v]x{[i] + sin(a)/d*[L] + ((1 - cos(a))/(d*d)*([L]x[L]))} 
(defn dot-product [v1 v2]
  (apply + (map #(* (nth v1 %1) (nth v2 %1))
		(take (count v1) nums))))

; a × b = (a2b3 − a3b2) i + (a3b1 − a1b3) j + (a1b2 − a2b1) k = (a2b3 − a3b2, a3b1 − a1b3, a1b2 − a2b1).
(defn cross-product [a b]
  [(- (* (nth a 1) (nth b 2)) (* (nth a 2) (nth b 1)))
   (- (* (nth a 2) (nth b 0)) (* (nth a 0) (nth b 2)))
   (- (* (nth a 0) (nth b 1)) (* (nth a 1) (nth b 0)))])

(defn row [m n]
  (nth m n))

(defn column [m n]
    (map #(nth %1 n) m))

(defn *matrices [m1 m2]
  (let [cols (count (first m2))]
    (defn *row [r]
      (map #(dot-product (row m1 r) (column m2 %1))
	   (take cols nums)))
    (map *row (take (count m1) nums))))

(defn +matrices [m1 m2]
  (let [cols (count (first m2))]
    (defn +row [r]
      (map #(+ (nth (row m1 r) %1) (nth (row m2 r) %1))
	   (take cols nums)))
    (map +row (take (count m1) nums))))

(defn *scalar [m s]
  (defn *row [r]
    (map #(* s %1) r))
  (map *row m))

(defn transpose [m]
  (map #(column m %1) (take (count (first m)) nums)))

;    [v] = [v]x{[i] + sin(a)/d*[L] + ((1 - cos(a))/(d*d)*([L]x[L]))} 
(defn rotate [v axis angle]
  (let [L [[0                (nth axis 2)      (- (nth axis 1))]
	  [(- (nth axis 2))  0                 (nth axis 0)]
	  [(nth axis 1)      (- (nth axis 0))  0]]]
    (let [d (length (butlast axis))]
      (first (*matrices [(butlast v)]
			(+matrices (+matrices I
					      (*scalar L (/ (Math/sin angle) d)))
				   (*scalar (*matrices L L)
					    (/ (- 1 (Math/cos angle)) (* d d)))))))))
  
(defn give-me-tree [node depth]
  (if (> depth max-depth) node
      
      (let [d (/ (last node) (nth primes depth))]
	(let [first-branch (make-node (normalize (vector-between-points
						  (butlast (travel node 0))
						  (point-on-plane (plane node))))
				      d)]

	  (defn make-branches []
	    (loop [branches [first-branch]
		   n (dec (nth primes depth))
		   angle (/ 360 (nth primes depth))]
	      (if (= n 0) branches
		  (recur (cons (make-node (rotate (last branches)
						  node
						  angle)
					  d)
			       branches)
			 (dec n)
			 angle))))

	  (let [up-angle (/ 180 (nth primes depth))]
	    (concat [node] [(map #(give-me-tree (rotate %1
							(cross-product %1 node)
							up-angle)
						(inc depth))
				 (make-branches))]))))))

;(println (cross-product [1 2 3] [1 2 3]))

(println (give-me-tree [1 2 3 5] 0))